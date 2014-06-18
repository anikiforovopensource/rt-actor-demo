package com.pagerduty.sonar

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Address
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus


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
