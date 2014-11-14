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
import akka.util.Timeout
import scala.concurrent.duration._
import com.pagerduty.sonar._
import com.pagerduty.sonar.demo.model._
import java.awt.Color
import com.pagerduty.sonar.demo.Demo.Colors


/**
 * @author Aleksey Nikiforov
 */
object DisplayAdapter {
  case object NextFrame

  case class Frame(
      /** Actors per node. */
      actorsByNode: Map[Int, Set[DisplaySlot]],
      /** Links from actor to actor. */
      links: Set[DisplayLink])

  object Frame {
    val empty = DisplayAdapter.Frame(Map.empty, Set.empty)
  }
}


/**
 * Actor that transform current visualization state into a single frame.
 */
class DisplayAdapter(val commView: ActorRef) extends Actor with ActorLogging {
  import DisplayAdapter._
  import context.dispatcher

  implicit val askTimeout = Timeout(1.second)

  def receive = {
    case NextFrame =>
      val state = (commView ? CommView.GetState).mapTo[CommView.State]
      state.map { case CommView.State(actors, links) =>
        stateToFrame(actors, links)
      } pipeTo sender
  }

  def stateToFrame(actors: Set[ActorRef], links: Map[ActorLink, LinkInfo]): Frame = {
    val nodes = actors.map(_.path.address)
    val demoNode = actors.find(_.path.name == HeartbeatSrc.ActorName).getOrElse(
      return Frame.empty
    ).path.address

    val sonarNodes = (nodes - demoNode).toSeq.sortBy(_.toString)
    val sonarNodeMap = sonarNodes.zipWithIndex.map { case (node, index) => node -> (index + 1) }
    val nodeMap: Map[Address, Int] = Map(demoNode -> 0) ++ sonarNodeMap

    def toDisplaySlot(actor: ActorRef): DisplaySlot = {
      val nodeId = nodeMap(actor.path.address)
      def slot(position: Pos, radius: Double, color: Color) = {
        DisplaySlot(nodeId, position, radius, color, Colors.Outline, 0)
      }

      actor.path.name match {
        case HeartbeatSrc.ActorName =>
          slot(Pos(0.5, 0.05), 0.03, Colors.ExternalClients)

        case AlertingSystem.ActorName =>
          slot(Pos(0.5, 1 - 0.05), 0.03, Colors.ExternalAlertingSystem)

        case Supervisor.ActorName =>
          slot(Pos(0.5, 0.1), 0.03, Colors.Supervisor)

        case BucketRouter.ActorName(i) =>
          slot(Pos(0.25 + i.toInt*0.25, 0.275), 0.025, Colors.Router)

        case Bucket.ActorName(i) =>
          val id = i.toInt
          val x = (id % Bucket.BucketCount)*0.12 + 0.2
          val y = 0.45
          slot(Pos(x, y), 0.025, Colors.Bucket)

        case Watcher.ActorName(i) =>
          val id = i.toInt
          val x = (id % Bucket.BucketCount)*0.12 + 0.2
          val y = (id / Bucket.BucketCount)*0.05 + 0.625
          slot(Pos(x, y), 0.015, Colors.Watcher)

        case AlertManager.ActorName =>
          slot(Pos(0.5, 1 - 0.1), 0.03, Colors.AlertManager)
      }
    }
    def toDisplayLink(link: ActorLink, info: LinkInfo): DisplayLink = {
      DisplayLink(toDisplaySlot(link.a), toDisplaySlot(link.b), info)
    }

    val allLinks = links.map { case (link, info) => toDisplayLink(link, info) }.toSet
    val selfLinksHeat = allLinks.collect {
      case DisplayLink(a, b, LinkInfo(heat, _)) if (a == b) => a -> heat
    }.toMap

    val actorSlots = actors.map(toDisplaySlot)
    val actorsWithSelfLinked = actorSlots.map {
      case slot: DisplaySlot if selfLinksHeat.contains(slot) =>
        slot.copy(borderColor = Colors.Message, heat = selfLinksHeat(slot))
      case other =>
        other
    }
    val resActors = actorsWithSelfLinked.groupBy(_.node)

    val resLinks = allLinks.filter {
      case DisplayLink(a, b, _) => actorSlots.contains(a) && actorSlots.contains(b)
    }

    Frame(resActors, allLinks)
  }
}
