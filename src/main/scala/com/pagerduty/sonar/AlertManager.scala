package com.pagerduty.sonar

import akka.actor._
import akka.util.Timeout
import akka.pattern._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.control.NonFatal
import com.pagerduty.sonar.demo.CommLogger


object AlertManager {
  val ActorName = "alertManager"

  case class WireInAlertingSystem(alertingSystem: ActorRef)

  case class PossibleAlert(address: Int)
  case class ConfirmedAlert(address: Int)
  case class Alert(address: Int)


  def getRef(context: ActorContext): ActorRef = {
    context.actorFor(s"/user/${ActorName}")
  }

  def forNode(address: Address, usingCtx: ActorContext): ActorRef = {
    val path = s"$address/user/$ActorName"
    usingCtx.actorFor(path)
  }
}


class AlertManager(supervisor: ActorRef) extends CommLogger with Actor with ActorLogging {
  import AlertManager._
  import context.dispatcher

  implicit val askTimeout = Timeout(1.second)

  var alertingSystem = Option.empty[ActorRef]

  def receive = {
    case WireInAlertingSystem(alertingSystem) =>
      this.alertingSystem = Some(alertingSystem)

    case PossibleAlert(address) =>
      val verification = (supervisor.com ? Watcher.VerifyAlert(address)).recoverWith {
        case NonFatal(_) => Future.successful(ConfirmedAlert(address))
      }
      verification pipeTo self

    case Watcher.FalseAlarm(address) =>
      log.info(s"False alarm for ${address}, heartbeat verified by ${sender}.")

    case ConfirmedAlert(address) =>
      if (alertingSystem.isDefined) {
        alertingSystem.get.com ! Alert(address)
      }
      else {
        log.error("AlertingSystem is not wired in.")
      }
  }
}
