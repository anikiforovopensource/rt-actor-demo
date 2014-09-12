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
import akka.pattern._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._


/**
 * @author Aleksey Nikiforov
 */
object CommLogger {
  case class CommEvent(src: ActorRef, dest: ActorRef)
  case class CommSrc(actor: ActorRef)
  case object Subscribed
}


trait CommLogger extends Actor {
  import context.dispatcher

  private val mediator = DistributedPubSubExtension(context.system).mediator


  private def sendingTo(dest: ActorRef): Unit = {
    mediator ! Publish(CommView.Topic, CommLogger.CommEvent(self, dest))
  }

  val subscibe = context.system.scheduler.schedule(0.seconds, 0.5.second) {
    mediator ! Publish(CommView.Topic, CommLogger.CommSrc(self))
  }

  override def unhandled(message: Any): Unit = message match {
    case CommLogger.Subscribed => subscibe.cancel
    case any => super.unhandled(any)
  }

  class InterceptOps(val a: ActorRef) {
    def !(m: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      sendingTo(a)
      a.tell(m, sender)
    }
    def forward(m: Any)(implicit context: ActorContext): Unit = {
      sendingTo(a)
      a.forward(m)(context)
    }
    def ?(m: Any)(implicit timeout: Timeout): Future[Any] = {
      sendingTo(a)
      a.ask(m)(timeout)
    }
  }
  implicit class CommInterceptor(val a: ActorRef) {
    def com: InterceptOps = new InterceptOps(a)
  }
}
