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

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus


/**
 * @author Aleksey Nikiforov
 *
 * Contains common cluster event listener functionality.
 */
trait SonarClusterListener extends  Actor with ActorLogging {

  val sonarRole = "sonar"
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
  }

  override def postStop(): Unit = {
    cluster unsubscribe self
  }

  private var _sonarNodes = Set.empty[Address]
  def sonarNodes: Set[Address] = _sonarNodes
  def sonarNodes_=(nodes: Set[Address]): Unit = {
    if (_sonarNodes != nodes) {
      _sonarNodes = nodes
      sonarNodesChanged()
    }
  }

  def sonarNodesChanged(): Unit


  def handleClusterEvents: Receive = {
    case state: CurrentClusterState =>
      sonarNodes = state.members.collect {
        case m if m.status == MemberStatus.Up && m.hasRole(sonarRole) => m.address
      }

    case MemberUp(member) if member.hasRole(sonarRole) =>
      sonarNodes += member.address

    case MemberRemoved(member, _) if member.hasRole(sonarRole) =>
      sonarNodes -= member.address

    case _: MemberEvent =>
      // ignore
  }
}
