package com.pagerduty.sonar.demo

import akka.actor._
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import com.pagerduty.sonar._
import java.awt.Color


object Demo {

  // Console-adjustable values.
  @volatile var heatPerEvent = 300.0 // units.
  @volatile var heatDecaySpeed = 6.0 // units per second.

  @volatile var clientCount = 18
  @volatile var missingHeartbeatChance = 0.25

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

  // Console-runnable commands.
  def initDemoNode(): Unit = {
    if (demoNode == null) {
      demoNode = Cluster(initDemoSystem())
      demoNode.join(demoNode.selfAddress)
    }
    else {
      error("Demo node is already initialized.")
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
      error("Demo node is not initialized.")
    }
  }

  //ConfigFactory.

  def stopNode(i: Int): Unit = sonarNodes.synchronized {
    sonarNodes(i + 1).system.shutdown()
  }

  private var displayAdapter: ActorRef = null
  def showDisplay(): Unit = {
    if (demoNode != null) {
      val display = new SwingDisplay(displayAdapter)
      display.show()
    }
    else {
      error("Demo node is not initialized.")
    }
  }

  //================================================================================================
  //   Implementation
  //================================================================================================

  private val config = ConfigFactory.load()
  private val systemName = "sonarCluster"

  @volatile private var demoNode: Cluster = _
  @volatile private var sonarNodes = Seq.empty[Cluster]

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


  private def initDemoSystem(): ActorSystem = {
    val system = ActorSystem(systemName, config.getConfig("demo").withFallback(config))

    val commView = system.actorOf(Props[CommView], "commView")
    val heartbeatSrc = system.actorOf(Props[HeartbeatSrc], HeartbeatSrc.ActorName)
    val alertingSys = system.actorOf(Props[AlertingSystem], AlertingSystem.ActorName)
    system.actorOf(Props(new DemoClusterListener(heartbeatSrc, alertingSys)), "demoClusterListener")

    displayAdapter = system.actorOf(Props(new DisplayAdapter(commView)), "displayAdapter")

    system
  }

  private def initSonarSystem(): ActorSystem = {
    val system = ActorSystem(systemName, config.getConfig("sonar").withFallback(config))

    val supervisor = system.actorOf(Props[Supervisor], Supervisor.ActorName)
    system.actorOf(Props(new SonarClusterManager(supervisor)), "sonarClusterManager")
    system.actorOf(Props(new AlertManager(supervisor)), AlertManager.ActorName)

    system
  }
}
