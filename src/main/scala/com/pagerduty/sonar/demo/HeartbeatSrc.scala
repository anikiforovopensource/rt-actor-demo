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

package com.pagerduty.sonar.demo

import akka.actor._
import scala.concurrent.duration._
import scala.util.Random
import com.pagerduty.sonar.Supervisor.Heartbeat


/**
 * @author Aleksey Nikiforov
 */
object HeartbeatSrc {
  val ActorName = "externalClients"

  case object UpdateWorkers
  case class UpdateEndpoints(endpoings: IndexedSeq[ActorRef])
}


class HeartbeatSrc extends CommLogger with Actor with ActorLogging {
  import HeartbeatSrc._
  import context.dispatcher

  override def preStart(): Unit = {
    context.system.scheduler.schedule(2.seconds, 1.seconds)(self ! UpdateWorkers)
  }
  override def postRestart(reason: Throwable): Unit = {
    // Prevents preStart() being call on restart.
  }

  val random = new Random()
  var workers = IndexedSeq.empty[ActorRef]
  var endpoints = IndexedSeq.empty[ActorRef]

  def receive = {
    case UpdateWorkers =>
      if (Demo.clientCount != workers.size) updateWorkers(Demo.clientCount - workers.size)

    case UpdateEndpoints(endpoints) =>
      this.endpoints = endpoints

    case heartbeat: Heartbeat =>
      if (!endpoints.isEmpty) {
        val index = random.nextInt(endpoints.size)
        val dest = endpoints(index)
        dest.com ! heartbeat
      }
      else {
        log.warning("No endpoints to record heartbeats.")
      }
  }

  def updateWorkers(change: Int): Unit = {
    val sign = math.signum(change)
    val delta = math.abs(change)

    workers =
      if (sign < 0) {
        workers.takeRight(delta).foreach(context.stop(_))
        workers.dropRight(delta)
      }
      else {
        val size = workers.size
        val adding = for (i <- 0 until delta) yield mkWorker(size + i)
        workers ++ adding
      }
  }

  def mkWorker(address: Int) = context.actorOf(Props(
    new HeartbeatWorker(
      endpoint = self,
      address = address)))
}
