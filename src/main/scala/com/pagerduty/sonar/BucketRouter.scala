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
object BucketRouter {
  private val ActorNamePrefix = "router"
  def actorName(i: Int): String = ActorNamePrefix + i
  final val ActorName = s"$ActorNamePrefix(.*)".r

  case class SetRoute(route: Map[Int, Set[ActorRef]])
}


/**
 * This class is responsible for routing messages to local and remote buckets.
 */
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
