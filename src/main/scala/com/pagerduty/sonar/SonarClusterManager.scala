package com.pagerduty.sonar

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import com.pagerduty.sonar.Supervisor.ReBalance


class SonarClusterManager(val supervisor: ActorRef) extends SonarClusterListener {

  val address = cluster.selfAddress
  var isLeader = false


  def receive = handleClusterEvents

  def sonarNodesChanged(): Unit = {
    val ordered = sonarNodes.toSeq.sortBy(_.toString)
    isLeader = ordered.headOption.map(_ == address).getOrElse(false)

    if (isLeader) rebalanceLoad(address, sonarNodes)
  }

  def rebalanceLoad(leader: Address, nodes: Set[Address]): Unit = {
    val leaderSup = Supervisor.forNode(leader, context)
    val supervisers = nodes.map(Supervisor.forNode(_, context))
    leaderSup ! ReBalance(supervisers)
  }
}
