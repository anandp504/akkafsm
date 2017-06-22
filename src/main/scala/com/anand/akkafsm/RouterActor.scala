package com.anand.akkafsm

import akka.actor._
import akka.routing._

import scala.collection.mutable

class RouterActor extends Actor with ActorLogging {

  val messageBuffer = mutable.Queue[String]()

  var router = {
    log.info("Scheduled Report Router Initialized...")
    val routees = Vector.fill(20) {
      val worker = context.actorOf(Props[WorkerFSM])
      context watch worker
      ActorRefRoutee(worker)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case job: String => {
      messageBuffer.enqueue(job)
      if (messageBuffer.nonEmpty) {
        router.route(Broadcast(WorkAvailable), self)
      }
    }

    case RequestWork => {
      if (messageBuffer.nonEmpty) {
        sender ! messageBuffer.dequeue
      }
    }

    case Terminated(actor) => {
      log.info("Creating a new worker actor after a worker was terminated due to exception")
      router = router.removeRoutee(actor)
      val worker = context.actorOf(Props[WorkerFSM])
      context watch worker
      router = router.addRoutee(worker)
    }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info("Router actor is terminated...")
  }
}
