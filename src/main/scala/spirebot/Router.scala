package spirebot

import java.io.File
import scala.collection.mutable
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import Util._

class Router extends Actor {

  def receive = {
    case msg: Msg => handle(msg)
    case Terminated(actor) => println("$actor died")
  }

  val Cmd = """^([^ ]+) *(.*)$""".r

  def handle(msg: Msg): Unit = {
    val chan = msg.channel
    msg.text match {
      case Cmd("!", s) => repl(chan) ! Eval(s)

      case Cmd("@reload", _) => repl(chan) ! Reload
      case Cmd("@time", s) => repl(chan) ! Benchmark(s)
      case Cmd("@type", s) => repl(chan) ! ShowType(s)
      case Cmd("@show", s) => repl(chan) ! ShowTree(s)
      case Cmd("@dump", s) => repl(chan) ! DumpTree(s)

      case Cmd("@join", s) => auth(msg) { gateway ! Join(s) }
      case Cmd("@leave", _) => auth(msg) { gateway ! Leave(chan) }
      case Cmd("@quit", _) => auth(msg) { quit() }

      case Cmd("@help", _) => gateway ! Send(chan, helpMessage)

      case _ =>
    }
  }

  val helpMessage = """
    |! EXPR - evaluate EXPR in the repl (see also: @dump @show @time @type)
    |@help - show this message
    |@reload - reload the interpreter""".stripMargin.trim

  val owners: Set[String] = setting("owners", "d_m").split(",").toSet

  val imports: Seq[String] =
    Seq(new File(setting("imports", "imports.txt"))).
      filter(_.exists).
      flatMap(io.Source.fromFile(_).mkString.split("\n"))

  val repls = mutable.Map.empty[String, ActorRef]
  val gateway = context.system.actorOf(Props(classOf[Gateway], this.self))

  def auth(msg: Msg)(f: => Unit): Unit =
    if (owners.contains(msg.sender)) f

  def repl(chan: String): ActorRef =
    repls.getOrElseUpdate(chan, start(chan))

  def start(chan: String): ActorRef =
    context.system.actorOf(Props(classOf[Repl], chan, gateway, imports))

  def quit(): Unit = {
    gateway ! Quit
    repls.foreach { case (chan, repl) => repl ! Quit }
    context.stop(self)
    context.system.shutdown()
    System.exit(0)
  }
}
