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


object CommView {
  final val Topic = "displayEvents"

  case object GetState
  case class State(actors: Set[ActorRef], links: Map[ActorLink, LinkInfo])
}


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
