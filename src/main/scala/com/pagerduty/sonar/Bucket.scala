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

import akka.actor._
import com.pagerduty.sonar.Supervisor.Heartbeat
import com.pagerduty.sonar.demo.CommLogger


/**
 * @author Aleksey Nikiforov
 */
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
