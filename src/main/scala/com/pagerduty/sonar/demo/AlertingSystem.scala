package com.pagerduty.sonar.demo

import akka.actor._
import com.pagerduty.sonar.AlertManager.Alert


object AlertingSystem {
  final val ActorName = "externalAlertingSystem"
}


class AlertingSystem extends CommLogger with Actor with ActorLogging {

  def receive = {
    case Alert(address) =>
      log.info(s"ALERT FOR ADDRESS $address!")
  }
}
