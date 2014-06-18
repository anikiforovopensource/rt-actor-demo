package com.pagerduty.sonar.demo

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import scala.concurrent.duration._
import com.pagerduty.sonar.Supervisor.Heartbeat
import scala.concurrent.forkjoin.ThreadLocalRandom


object HeartbeatWorker {
  case object Tick
}


class HeartbeatWorker(val endpoint: ActorRef, val address: Int) extends Actor with ActorLogging {
  import HeartbeatWorker._
  import context.dispatcher

  def random(): Double = ThreadLocalRandom.current.nextDouble()
  val heartbeatIntervalMs = context.system.settings.config.getInt("com.pagerduty.sonar.heartbeatIntervalMs")

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce((random()*heartbeatIntervalMs).millis)(self ! Tick)
  }
  override def postRestart(reason: Throwable): Unit = {
    // Prevents preStart() being call on restart.
  }


  def receive = {
    case Tick =>
      context.system.scheduler.scheduleOnce(heartbeatIntervalMs.millis)(self ! Tick)

      if (random() > Demo.missingHeartbeatChance) {
        endpoint ! Heartbeat(address, System.currentTimeMillis)
      }
  }
}
