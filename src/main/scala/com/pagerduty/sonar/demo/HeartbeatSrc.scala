package com.pagerduty.sonar.demo

import akka.actor._
import scala.concurrent.duration._
import scala.util.Random
import com.pagerduty.sonar.Supervisor.Heartbeat


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
