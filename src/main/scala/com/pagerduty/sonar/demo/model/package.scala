package com.pagerduty.sonar.demo

import akka.actor.ActorRef
import java.awt.Color


package model {
  case class BidirectionalActorLink private[model] (a: ActorRef, b: ActorRef) {
    override def toString(): String = s"${a.path}<->${b.path}"
  }

  object ActorLink {
    def apply(a: ActorRef, b: ActorRef): BidirectionalActorLink = {
      val seq = Seq(a, b).sortBy(_.hashCode)
      new BidirectionalActorLink(seq.head, seq.last)
    }
  }

  case class LinkInfo(heat: Double, lastUpdated: Long) {
    def update(increment: Double, now: Long): LinkInfo = {
      val secondsPassed = (now - lastUpdated)*0.001
      val currentHeat = math.max(0, heat - secondsPassed*Demo.heatDecaySpeed)
      val newHeat = math.log(math.exp(currentHeat) + increment)
      LinkInfo(newHeat, now)
    }
  }

  case class Pos(x: Double, y: Double)

  case class DisplaySlot(
    node: Int, position: Pos,
    radius: Double,
    fillColor: Color,
    borderColor: Color, heat: Double)

  case class DisplayLink(a: DisplaySlot, b: DisplaySlot, info: LinkInfo)
}


package object model {
  type ActorLink = BidirectionalActorLink
}
