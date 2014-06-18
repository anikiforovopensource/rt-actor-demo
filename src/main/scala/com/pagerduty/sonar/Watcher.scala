package com.pagerduty.sonar

import akka.actor._
import com.pagerduty.sonar.Supervisor.Heartbeat
import scala.concurrent.duration._
import com.pagerduty.sonar.demo.CommLogger


object Watcher {
  private val ActorNamePrefix = "watcher"
  def actorName(i: Int): String = ActorNamePrefix + i
  final val ActorName = s"$ActorNamePrefix(.*)".r

  case object HeartbeatTimeout
  case class VerifyAlert(address: Int)
  case class FalseAlarm(address: Int)
}

class Watcher(val address: Int) extends CommLogger with Actor with ActorLogging {
  import Watcher._
  import context.dispatcher

  val alertAfterDuration = context.system.settings.config.getInt(
    "com.pagerduty.sonar.alertAfterDurationMs").millis

  var heartbeatTimeout = nextTimeout()
  var lastHeartbeat = System.currentTimeMillis

  override def postStop(): Unit = {
    heartbeatTimeout.cancel
    super.postStop()
  }

  def nextTimeout(): Cancellable = {
    context.system.scheduler.scheduleOnce(alertAfterDuration)(self.com ! HeartbeatTimeout)
  }

  def receive = {
    case heartbeat @ Heartbeat(address, timeStamp) if this.address == address =>
      heartbeatTimeout.cancel
      heartbeatTimeout = nextTimeout()
      lastHeartbeat = timeStamp

    case HeartbeatTimeout =>
      AlertManager.getRef(context).com ! AlertManager.PossibleAlert(address)

    case VerifyAlert(address) if this.address == address =>
      val sinceLastHeartbeat = System.currentTimeMillis - lastHeartbeat
      if (sinceLastHeartbeat < alertAfterDuration.toMillis) sender.com ! FalseAlarm(address)
  }
}
