package com.pagerduty.sonar.demo

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._
import com.pagerduty.sonar._
import com.pagerduty.sonar.demo.model._
import java.awt.Color
import com.pagerduty.sonar.demo.Demo.Colors


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
