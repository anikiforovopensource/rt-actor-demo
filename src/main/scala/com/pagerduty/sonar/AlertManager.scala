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
import akka.pattern._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.control.NonFatal
import com.pagerduty.sonar.demo.CommLogger


/**
 * @author Aleksey Nikiforov
 */
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
