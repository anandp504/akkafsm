package com.anand.akkafsm

import akka.actor.ActorSystem

object Boot extends App {

  val actorSystem = ActorSystem("TestFSM")
  val router = RouterService(actorSystem).instance

  Thread.sleep(10000)

  for(i <- 1 to 500) {
    router ! Job(s"msg-$i")
  }

}
