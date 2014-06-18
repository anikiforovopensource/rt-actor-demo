package com.pagerduty.sonar

import akka.actor._
import com.pagerduty.sonar.Supervisor.Heartbeat
import com.pagerduty.sonar.demo.CommLogger


object Bucket {
  private val ActorNamePrefix = "bucket"
  def actorName(i: Int): String = ActorNamePrefix + i
  final val ActorName = s"$ActorNamePrefix(.*)".r


  final val AllBuckets: Set[Int] = (0 until 6).toSet
  final val BucketCount: Int = AllBuckets.size

  def hash(address: Int): Int = {
    address
  }

  def bucketIdForHash(hash: Int): Int = {
    hash % BucketCount
  }

  def bucketIdForAddress(address: Int): Int = {
    bucketIdForHash(hash(address))
  }
}


class Bucket(val id: Int) extends CommLogger with Actor with ActorLogging {
  import Bucket._

  var watchers = Map.empty[Int, ActorRef]

  def receive = {
    case heartbeat @ Heartbeat(address, _) =>
      val watcher = watchers.get(address).getOrElse {
        val watcher = context.actorOf(Props(new Watcher(address)), Watcher.actorName(address))
        watchers += address -> watcher
        watcher
      }
      watcher.com forward heartbeat

    case verify @ Watcher.VerifyAlert(address) =>
      watchers.get(address).foreach { watcher =>
        watcher.com forward verify
      }
  }
}
