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

import akka.actor.ActorRef
import scala.util.Random


/**
 * @author Aleksey Nikiforov
 *
 * A proper allocation strategy has not been implemented. Instead we use canned data that looks
 * pretty when demoing.
 */
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
      // This is a fallback strategy for the demo.
      // The proper allocation strategy would be more involved.
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
