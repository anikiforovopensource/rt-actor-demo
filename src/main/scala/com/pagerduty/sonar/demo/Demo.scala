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
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import com.pagerduty.sonar._
import java.awt.Color


/**
 * @author Aleksey Nikiforov
 */
object Demo {

  // Console-adjustable values.
  @volatile var heatPerEvent = 300.0 // units.
  @volatile var heatDecaySpeed = 6.0 // units per second.

  @volatile var clientCount = 18
  @volatile var missingHeartbeatChance = 0.25 // Probability (x*100%).

  // Display adjustments.
  object Colors {
    val Background = Color.BLACK
    val Message = new Color(0, 255, 0)
    val Outline = Color.LIGHT_GRAY

    val ExternalClients = Color.GRAY
    val ExternalAlertingSystem = new Color(128, 0, 0)
    val AlertManager = new Color(192, 105, 0)

    val Supervisor = new Color(128, 0, 128)
    val Router = new Color(0, 0, 128)
    val Bucket = new Color(0, 160, 160)
    val Watcher = new Color(212, 212, 212)
  }
  val LineWidthScale = 0.0015


  //================================================================================================
  //   Demo startup and shutdown.
  //================================================================================================

  @volatile private var demoNode: Cluster = _
  @volatile private var sonarNodes = Seq.empty[Cluster]

  // Console-runnable commands.
  def initDemoNode(): Unit = {
    if (demoNode == null) {
      demoNode = Cluster(initDemoSystem())
      demoNode.join(demoNode.selfAddress)
    }
    else {
      sys.error("Demo node is already initialized.")
    }
  }

  def addNode(): Int = sonarNodes.synchronized {
    if (demoNode != null) {
      val node = Cluster(initSonarSystem())
      sonarNodes :+= node
      node.join(demoNode.selfAddress)
      sonarNodes.size
    }
    else {
      sys.error("Demo node is not initialized.")
    }
  }

  def stopNode(i: Int): Unit = sonarNodes.synchronized {
    sonarNodes(i + 1).system.shutdown()
  }

  def showDisplay(): Unit = {
    if (demoNode != null) {
      val display = new SwingDisplay(displayAdapter)
      display.show()
    }
    else {
      sys.error("Demo node is not initialized.")
    }
  }


  def main(args: Array[String]): Unit = {
    initDemoNode()
    showDisplay()
    Thread.sleep(1000)

    addNode()
    Thread.sleep(10000)

    addNode()
    Thread.sleep(10000)

    addNode()
    Thread.sleep(10000)

    addNode()
  }


  //================================================================================================
  //   Implementation.
  //================================================================================================

  private var displayAdapter: ActorRef = null

  private val config = ConfigFactory.load()
  private val systemName = "sonarCluster"


  private def initDemoSystem(): ActorSystem = {
    // This node is used to collect visualization data and simulate external system.
    val system = ActorSystem(systemName, config.getConfig("demo").withFallback(config))

    // Simulates sources of hearbeats.
    val heartbeatSrc = system.actorOf(Props[HeartbeatSrc], HeartbeatSrc.ActorName)
    // Simulates alerting system.
    val alertingSys = system.actorOf(Props[AlertingSystem], AlertingSystem.ActorName)
    // This actor listens to cluster events and wires nodes with simulated endpoints.
    system.actorOf(Props(new DemoClusterListener(heartbeatSrc, alertingSys)), "demoClusterListener")

    // This actor collects visualization data.
    val commView = system.actorOf(Props[CommView], "commView")
    // This actor converts current visualization state into frames.
    displayAdapter = system.actorOf(Props(new DisplayAdapter(commView)), "displayAdapter")

    system
  }

  private def initSonarSystem(): ActorSystem = {
    // System node.
    val system = ActorSystem(systemName, config.getConfig("sonar").withFallback(config))

    // Supervisor will spawn most other actors.
    val supervisor = system.actorOf(Props[Supervisor], Supervisor.ActorName)

    // This actor listens to cluster events and delegates work to the supervisor.
    system.actorOf(Props(new SonarClusterManager(supervisor)), "sonarClusterManager")
    // Alert manager.
    system.actorOf(Props(new AlertManager(supervisor)), AlertManager.ActorName)

    system
  }
}
