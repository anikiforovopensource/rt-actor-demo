package com.pagerduty.sonar

import akka.actor.ActorRef
import scala.util.Random


object AllocationStrategy {

  /** SupervisorId -> BucketIds */
  type Allocation = Map[ActorRef, Set[Int]]
  object Allocation { val empty = Map.empty[ActorRef, Set[Int]] }

  case class MigrationStrategy(unionPhase: Allocation, finalPhase: Allocation)


  val replicationFactor = 2


  def reassign(allocation: Allocation): MigrationStrategy = {
    if (allocation.size <= replicationFactor) {
      val fullHouse = allocation.keys.map(supervisor => supervisor -> Bucket.AllBuckets).toMap
      MigrationStrategy(fullHouse, fullHouse)
    }
    else if (allocation.size == 3) {
      // This is a pretty looking strategy for the demo.
      val supervisors = allocation.keys.toSeq.sortBy(_.path)
      val finalPhase = Map(
        supervisors(0) -> Set(0, 1, 4, 5),
        supervisors(1) -> Set(0, 2, 3, 5),
        supervisors(2) -> Set(1, 2, 3, 4))

      val unionPhase = allocation.map { case (node, buckets) =>
        val union = finalPhase(node) ++ buckets
        node -> union
      }

      MigrationStrategy(unionPhase, finalPhase)
    }
    else if (allocation.size == 4) {
      // This is a pretty looking strategy for the demo.
      val supervisors = allocation.keys.toSeq.sortBy(_.path)
      val finalPhase = Map(
        supervisors(0) -> Set(0, 1, 5),
        supervisors(1) -> Set(0, 3, 5),
        supervisors(2) -> Set(1, 2, 4),
        supervisors(3) -> Set(2, 3, 4))

      val unionPhase = allocation.map { case (node, buckets) =>
        val union = finalPhase(node) ++ buckets
        node -> union
      }

      MigrationStrategy(unionPhase, finalPhase)
    }
    else {
      // This is a fallback strategy for the demo. The proper allocation strategy is more involved.
      val random = new Random
      val nodeCount = allocation.size
      val nodeList = allocation.keys.toList

      var newAlloc = Set.empty[(ActorRef, Int)]

      for (bucket <- Bucket.AllBuckets) {
        var nodes = nodeList
        for (i <- 0 until replicationFactor) {
          val node :: rest = random.shuffle(nodeList)
          nodes = rest
          newAlloc += node -> bucket
        }
      }

      val finalPhase = newAlloc.groupBy { case (node, _) => node }.toMap
        .map { case (node, set) =>
          node -> set.map { case (_, bucket) => bucket }
        }

      val unionPhase = allocation.map { case (node, buckets) =>
        val union = finalPhase(node) ++ buckets
        node -> union
      }

      MigrationStrategy(unionPhase, finalPhase)
    }
  }
}
