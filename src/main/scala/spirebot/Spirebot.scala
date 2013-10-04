package spirebot

import akka.actor.{ActorSystem, Props}

object Spirebot {
  def main(args: Array[String]) {
    val system = ActorSystem("spirebot")
    val router = system.actorOf(Props(classOf[Router]))
  }
}
