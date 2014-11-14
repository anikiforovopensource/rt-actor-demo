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
import akka.actor.Status.Failure
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.contrib.pattern.DistributedPubSubMediator.SubscribeAck
import akka.util.Timeout
import com.pagerduty.sonar.demo.model._
import scala.concurrent.duration._


/**
 * @author Aleksey Nikiforov
 */
object CommView {
  final val Topic = "displayEvents"

  case object GetState
  case class State(actors: Set[ActorRef], links: Map[ActorLink, LinkInfo])
}


/**
 * Keeps track of active actors and their messages.
 */
class CommView extends Actor with ActorLogging {
  import CommView._
  import context.dispatcher

  val mediator = DistributedPubSubExtension(context.system).mediator

  implicit val askTimeout = Timeout(1.second)
  mediator ? Subscribe(Topic, self) pipeTo self

  def waiting: Receive = {
    case ack: SubscribeAck =>
      log.info("Subscribed to message event stream.")
      context.become(receive)

    case Failure(e) =>
      log.error(e, "Unable to subsribe to message event stream.")
  }

  context.become(waiting)


  var actors = Set.empty[ActorRef]
  var links = Map.empty[ActorLink, LinkInfo]

  def receive: Receive = {
    case CommLogger.CommSrc(actor) =>
      context.watch(actor)
      actors += actor
      actor ! CommLogger.Subscribed

    case Terminated(actor) =>
      actors -= actor

    case CommLogger.CommEvent(src, dest) =>
      val link = ActorLink(src, dest)
      val now = System.currentTimeMillis
      val info = links.get(link).getOrElse(LinkInfo(0, 0))
      links += link -> info.update(Demo.heatPerEvent, now)

    case GetState =>
      val now = System.currentTimeMillis
      links = updateActorLinks(links, now)
      sender ! State(actors, links)
  }

  def updateActorLinks(links: Map[ActorLink, LinkInfo], now: Long)
  : Map[ActorLink, LinkInfo] = {
    links
      .map { case (link, info) => link -> info.update(0, now) }
      .filter { case (_, info) => info.heat > 0 }
  }
}
