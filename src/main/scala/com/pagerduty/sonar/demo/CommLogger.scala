package com.pagerduty.sonar.demo

import akka.actor._
import akka.pattern._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.contrib.pattern.DistributedPubSubMediator.Subscribe
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._


object CommLogger {
  case class CommEvent(src: ActorRef, dest: ActorRef)
  case class CommSrc(actor: ActorRef)
  case object Subscribed
}


trait CommLogger extends Actor {
  import context.dispatcher

  private val mediator = DistributedPubSubExtension(context.system).mediator


  private def sendingTo(dest: ActorRef): Unit = {
    mediator ! Publish(CommView.Topic, CommLogger.CommEvent(self, dest))
  }

  val subscibe = context.system.scheduler.schedule(0.seconds, 0.5.second) {
    mediator ! Publish(CommView.Topic, CommLogger.CommSrc(self))
  }

  override def unhandled(message: Any): Unit = message match {
    case CommLogger.Subscribed => subscibe.cancel
    case any => super.unhandled(any)
  }

  class InterceptOps(val a: ActorRef) {
    def !(m: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      sendingTo(a)
      a.tell(m, sender)
    }
    def forward(m: Any)(implicit context: ActorContext): Unit = {
      sendingTo(a)
      a.forward(m)(context)
    }
    def ?(m: Any)(implicit timeout: Timeout): Future[Any] = {
      sendingTo(a)
      a.ask(m)(timeout)
    }
  }
  implicit class CommInterceptor(val a: ActorRef) {
    def com: InterceptOps = new InterceptOps(a)
  }
}
