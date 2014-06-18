package com.pagerduty.sonar

import akka.actor._
import com.pagerduty.sonar.Supervisor.Heartbeat
import com.pagerduty.sonar.demo.CommLogger


object BucketRouter {
  private val ActorNamePrefix = "router"
  def actorName(i: Int): String = ActorNamePrefix + i
  final val ActorName = s"$ActorNamePrefix(.*)".r

  case class SetRoute(route: Map[Int, Set[ActorRef]])
}


class BucketRouter extends CommLogger with Actor with ActorLogging {
  import BucketRouter._

  var route = Map.empty[Int, Set[ActorRef]]

  def forward(address: Int, msg: Any): Unit = {
    val bucketId = Bucket.bucketIdForAddress(address)
    val replicas = route.get(bucketId)

    if (replicas.isDefined) replicas.get.foreach(_.com forward msg)
    else log.warning(s"No route for bucket=${bucketId} address=${address}.")
  }

  def receive = {
    case heartbeat @ Heartbeat(address, _) =>
      forward(address, heartbeat)

    case verify @ Watcher.VerifyAlert(address) =>
      forward(address, verify)

    case SetRoute(route) =>
      this.route = route
  }
}
