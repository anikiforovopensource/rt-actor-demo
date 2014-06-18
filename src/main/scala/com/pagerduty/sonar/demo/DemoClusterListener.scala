package com.pagerduty.sonar.demo

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Address
import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.cluster.ClusterEvent.MemberEvent
import com.pagerduty.sonar.SonarClusterListener
import com.pagerduty.sonar.demo.HeartbeatSrc.UpdateEndpoints
import com.pagerduty.sonar.Supervisor
import com.pagerduty.sonar.AlertManager
import com.pagerduty.sonar.AlertManager.WireInAlertingSystem



class DemoClusterListener(val heartbeatSrc: ActorRef, val alertingSystem: ActorRef)
  extends SonarClusterListener
{

  def receive = handleClusterEvents

  def sonarNodesChanged(): Unit = {
    val endpoints = sonarNodes.toIndexedSeq.map(Supervisor.forNode(_, context))
    heartbeatSrc ! UpdateEndpoints(endpoints)

    val alertManagers = sonarNodes.toIndexedSeq.map(AlertManager.forNode(_, context))
    alertManagers.foreach(_ ! WireInAlertingSystem(alertingSystem))
  }
}
