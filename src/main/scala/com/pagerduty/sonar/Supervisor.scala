/*
 * Copyright (c) 2014, PagerDuty
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pagerduty.sonar

import akka.actor._
import akka.util.Timeout
import akka.cluster.Cluster
import akka.pattern._
import com.pagerduty.sonar.demo.CommLogger
import com.pagerduty.sonar.AllocationStrategy.Allocation
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.Status.Failure
import com.pagerduty.sonar.BucketRouter.SetRoute
import scala.util.Random
import scala.concurrent.forkjoin.ThreadLocalRandom


/**
 * @author Aleksey Nikiforov
 */
object Supervisor {
  final val ActorName = "supervisor"

  case class MigrationId(leader: ActorRef, timeStamp: Long)

  case class ReBalance(supervisers: Set[ActorRef])
  case class InitMigration(migrationId: MigrationId)
  case class MigrationConfirmed(migrationId: MigrationId)
  case class GetStatus(migrationId: MigrationId)
  case class Watching(supervisor: ActorRef, buckets: Set[Int])
  case class CombinedStatus(migrationId: MigrationId, result: Set[Watching])
  case class SetUnionPhase(migrationId: MigrationId, map: Allocation)
  case class SetFinalPhase(migrationId: MigrationId, map: Allocation)
  case class FinalizeMigration(migrationId: MigrationId)

  case class Heartbeat(address: Int, timeStamp: Long)


  def forNode(address: Address, usingCtx: ActorContext): ActorRef = {
    val path = s"$address/user/$ActorName"
    usingCtx.actorFor(path)
  }
}


class Supervisor extends CommLogger with Actor with ActorLogging {
  import Supervisor._
  import context.dispatcher

  implicit val askTimeout = Timeout(1.second)

  val unionPhaseDuration = {
    val config = context.system.settings.config
    val alertAfterDurationMs = config.getInt("com.pagerduty.sonar.alertAfterDurationMs")
    (2.1 * alertAfterDurationMs).toInt.millis
  }

  var finalizer = Option.empty[Cancellable]
  var allocation = Allocation.empty
  var buckets = Map.empty[Int, ActorRef]
  val routers = for (i <- 0 until 3) yield {
    context.actorOf(Props(new BucketRouter), BucketRouter.actorName(i))
  }
  def randomRouter = {
    val index = ThreadLocalRandom.current.nextInt(routers.size)
    routers(index)
  }


  def always: Receive = {
    case heartbeat: Heartbeat =>
      randomRouter.com forward heartbeat

    case verify: Watcher.VerifyAlert =>
      randomRouter.com forward verify

    case ReBalance(supervisers) =>
      val id = MigrationId(self, System.currentTimeMillis)
      val init = InitMigration(id)
      val initAcks = Future.sequence(supervisers.map(_.com ? init)).mapTo[Set[MigrationConfirmed]]
      initAcks.map(_ => MigrationConfirmed(id)) pipeTo self // TODO handle failures
      context.become(leading(id, supervisers) orElse migrating(id) orElse always)

    case InitMigration(migrationId) =>
      context.become(migrating(migrationId) orElse always)
      finalizer.foreach(_.cancel())
      finalizer = None
      sender.com ! MigrationConfirmed(migrationId)
  }

  def leading(id: MigrationId, supervisers: Set[ActorRef]): Receive = {
    // Will override always.InitMigration.
    case InitMigration(migrationId) if migrationId == id =>
      sender.com ! MigrationConfirmed

    case MigrationConfirmed(migrationId) if migrationId == id =>
      val combined = Future.sequence(supervisers.map(_.com ? GetStatus(id))).mapTo[Set[Watching]]
      combined.map(results => CombinedStatus(id, results)) pipeTo self // TODO handle failures

    case CombinedStatus(migrationId, result) if migrationId == id =>
      val allocationMap = result.map { case Watching(supervisor, buckets) =>
        supervisor -> buckets
      }.toMap

      val strategy = AllocationStrategy.reassign(allocationMap)
      supervisers.foreach(_.com ! SetUnionPhase(id, strategy.unionPhase)) // TODO handle failures
      finalizer = Some(context.system.scheduler.scheduleOnce(unionPhaseDuration) {
        self.com ! FinalizeMigration(id)
      })
      context.become(
        pendingFinalization(id, supervisers, strategy.finalPhase) orElse
        migrating(id) orElse
        always)
  }

  def pendingFinalization(
      migration: MigrationId,
      supervisers: Set[ActorRef],
      finalAllocation: Allocation)
  : Receive = {
    case FinalizeMigration(migrationId) if migrationId == migration =>
      supervisers.foreach(_.com ! SetFinalPhase(migration, finalAllocation)) // TODO handle failures
  }

  def migrating(migration: MigrationId): Receive = {
    case GetStatus(migrationId) if migrationId == migration =>
      sender.com ! Watching(self, allocation.get(self).getOrElse(Set.empty))

    case p @ SetUnionPhase(migrationId, allocation) if migrationId == migration =>
      setAllocation(allocation)

    case p @ SetFinalPhase(migrationId, allocation) if migrationId == migration =>
      setAllocation(allocation)
      context.become(always)
  }

  def updateBuckets(): Unit = {
    val newBuckets = allocation(self)

    val remove = buckets.keySet -- newBuckets
    val (stop, keep) = buckets.partition { case (id, bucket) => remove.contains(id) }
    for ((_, bucket) <- stop) context.stop(bucket)

    val add = newBuckets -- buckets.keySet
    val started = for (id <- add) yield {
      id -> context.actorOf(Props(new Bucket(id)), Bucket.actorName(id))
    }

    buckets = keep ++ started
  }

  def setAllocation(allocation: Allocation): Unit = {
    this.allocation = allocation
    updateBuckets()

    val flat: Seq[(Int, ActorRef)] = allocation.toSeq.flatMap { case (supervisor, buckets) =>
      buckets.map(_ -> supervisor)
    }
    val supervisorRoute = flat.groupBy(_._1).map { case (bucket, set) =>
      bucket -> set.map(_._2).toSet
    }
    val route = supervisorRoute.map { case (bucketId, supervisors) =>
      bucketId -> supervisors.map { supervisor =>
        val path = s"${supervisor.path}/${Bucket.actorName(bucketId)}"
        context.system.actorFor(path)
      }
    }

    routers.foreach(_.com ! SetRoute(route))
  }

  def receive = always
}
