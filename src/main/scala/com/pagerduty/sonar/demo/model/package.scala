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

import akka.actor.ActorRef
import java.awt.Color


package model {

  /**
   * @author Aleksey Nikiforov
   */
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
    /** Id of the host node. */
    node: Int,
    /** Display position. */
    position: Pos,
    /** Radius of the circle representing the actor. */
    radius: Double,
    /** Color inside the actor circle. */
    fillColor: Color,
    /** Color around the actor circle. */
    borderColor: Color,
    /** When self-messaging turns the border into heat indicator. */
    heat: Double)

  case class DisplayLink(a: DisplaySlot, b: DisplaySlot, info: LinkInfo)
}


package object model {
  type ActorLink = BidirectionalActorLink
}
