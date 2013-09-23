package org.spirebot

import org.jibble.pircbot.PircBot

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}

import java.io.{File, PrintStream, ByteArrayOutputStream}

import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results._
import scala.tools.nsc.Settings
import scala.tools.reflect.ToolBox

object SettingUtil {
  def setting(name: String): Option[String] =
    Option(System.getProperty(name))
  def setting(name: String, default: String): String =
    setting(name).getOrElse(default)
  def parseInt(s: String): Option[Int] =
    if (s.matches("[0-9]+")) Some(s.toInt) else None
}

import SettingUtil._

case class Msg(channel: String, sender: String, login: String, hostname: String, text: String)

object Talker {
  case class Join(chan: String)
  case class Leave(chan: String)
  case class Send(chan: String, text: String)
  case class Msg(channel: String, sender: String, login: String, hostname: String, text: String)
}

class Talker(router: ActorRef) extends PircBot with Actor {
  val nick: String = setting("nick", "spirebot__")
  val owners: Set[String] = setting("owners", "d_m").split(",").toSet
  val channels: Seq[String] = setting("channels", "#spire-math").split(",")
  val server: String = setting("server", "irc.freenode.net")
  val port: Int = setting("port").flatMap(parseInt).getOrElse(6667)
  val password: Option[String] = setting("password")

  var done = false

  override def preStart() {
    setName(nick)
    setVerbose(true)
    setEncoding("UTF-8")
    connect()
    channels.foreach(joinChannel)
  }

  def connect(): Unit = password match {
    case Some(pass) => connect(server, port, pass)
    case None => connect(server, port)
  }

  override def onDisconnect: Unit = while (!done) {
    try {
      connect()
      return ()
    } catch {
      case e: Exception =>
        // TODO don't block a thread, schedule another attempt in 10s
        e.printStackTrace
        Thread.sleep(10000)
    }
  }

  override def postStop() {
    done = true
    disconnect()
    quitServer()
  }

  override def onPrivateMessage(sender: String, login: String, hostname: String, text: String) =
    router ! Msg(sender, sender, login, hostname, text)

  override def onMessage(channel: String, sender: String, login: String, hostname: String, text: String) =
    router ! Msg(channel, sender, login, hostname, text)

  import Talker._

  def sendLines(channel: String, text: String) {
    val lines = text.filter(_ != '\r').split("\n").filter(! _.isEmpty)
    lines.take(5).foreach { s =>
      if (s.startsWith("/me ")) sendAction(channel, s.substring(4))
      else sendMessage(channel, s)
    }
  }

  def receive = {
    case Join(chan) =>
      joinChannel(chan)
    case Leave(chan) =>
      partChannel(chan)
    case Send(chan, text) =>
      sendLines(chan, text)
  }
}

class Router extends Actor {

  val imports: Seq[String] =
    Seq(new File(setting("imports", "imports.txt"))).
      filter(_.exists).
      flatMap(io.Source.fromFile(_).mkString.split("\n"))

  def system = context.system
  val repls = mutable.Map.empty[String, ActorRef]

  var done: Boolean = false

  val realOut = System.out

  def outf(chan: String, s: String): Unit =
    realOut.println(s)

  def start(chan: String): ActorRef =
    system.actorOf(Props(classOf[Repl], chan, outf _, Nil))

  def repl(chan: String): ActorRef =
    repls.getOrElseUpdate(chan, start(chan))

  def eval(chan: String, s: String): Unit =
    repl(chan) ! Repl.Eval(s)

  def shutdown(): Unit =
    system.shutdown()

  def receive = {
    case Msg(chan, _, _, _, text) =>
      eval(chan, text)
    case Terminated(actor) =>
  }

  // def showType(channel: String, text: String) {
  //   useRepl(channel) { (repl, _) =>
  //     sendLines(channel, repl.typeOfExpression(text).toString)
  //   }
  // }

  // def showTree(channel: String, text: String) {
  //   useRepl(channel) { (repl, out) =>
  //     repl.interpret(s"unpack(reify { $text })") match {
  //       case Success => sendLines(channel, out.toString.replaceAll("^[^=]* = *", "").replace(" >: Nothing <: Any", ""))
  //       case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
  //       case Incomplete => sendLines(channel, "error: incomplete expression")
  //     }
  //   }
  // }

  // def dumpTree(channel: String, text: String) {
  //   useRepl(channel) { (repl, out) =>
  //     repl.interpret(s"showRaw(unpack(reify { $text }))") match {
  //       case Success => sendLines(channel, out.toString.replaceAll("^[^=]* = *", ""))
  //       case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
  //       case Incomplete => sendLines(channel, "error: incomplete expression")
  //     }
  //   }
  // }

  val helpMessage = """
! EXPR - evaluate EXPR in the repl (see also: @dump @show @time @type)
@help - show this message
@reload - reload the interpreter
""".trim

  // def showHelp(channel: String): Unit = sendLines(channel, helpMessage)

  // val Cmd = """^([^ ]+) *(.*)$""".r

  // def handle(msg: Msg): Unit = msg.text match {
  //   case Cmd("!", expr) => eval(msg.channel, expr)
  //   case Cmd("@type", expr) => showType(msg.channel, expr)
  //   case Cmd("@show", expr) => showTree(msg.channel, expr)
  //   case Cmd("@dump", expr) => dumpTree(msg.channel, expr)
  //   case Cmd("@time", expr) => eval(msg.channel, s"timer { $expr }")
  //   case Cmd("@help", _) => showHelp(msg.channel)
  //   case Cmd("@reload", _) => reload(msg.channel)
  //   case Cmd("@quit", _) => if (owners.contains(msg.sender)) quit()
  //   case Cmd(s, _) if s.startsWith("@") || s.startsWith("!") => showHelp(msg.channel)
        
  //   case _ =>
  // }
}

object Repl {
  case class Eval(s: String)
  case object Restart
}

class Repl(chan: String, outf: (String, String) => Unit, imports: Seq[String]) extends Actor {

  private[this] val SystemOut = System.out
  private[this] val SystemErr = System.err
  private[this] val ConsoleOut = Console.out
  private[this] val ConsoleErr = Console.err
  private[this] val baos = new ByteArrayOutputStream
  private[this] val ps = new PrintStream(baos, true, "UTF-8")

  private[this] var imain: Option[IMain] = None

  private[this] val settings: Settings = {
    val cfg = new Settings(s => System.err.println(s))
    cfg.usejavacp.value = true
    cfg.deprecation.value = true
    cfg.feature.value = false
    cfg.pluginsDir.value = "plugins"
    cfg
  }

  def startIMain: IMain = {
    val im = new IMain(settings)
    imports.foreach(im.quietImport(_))
    im
  }

  def use(f: (IMain, ByteArrayOutputStream) => Unit): Unit = {
    val im = imain match {
      case Some(im) =>
        im
      case None =>
        val im = startIMain
        imain = Some(im)
        im
    }
    captureOutput(f(im, baos))
  }

  def captureOutput(block: => Unit): Unit = try {
    redirectOutputs()
    block
  } finally {
    resetOutputs()
  }

  def redirectOutputs(): Unit = {
    System.setOut(ps)
    System.setErr(ps)
    Console.setOut(ps)
    Console.setErr(ps)
  }

  def resetOutputs(): Unit = {
    System.setOut(SystemOut)
    System.setErr(SystemErr)
    Console.setOut(ConsoleOut)
    Console.setErr(ConsoleErr)
    ps.flush()
    baos.reset()
  }

  def eval(text: String): Unit = use { (im, out) =>
    val result: String = im.interpret(text) match {
      case Success => out.toString.replaceAll("^res[0-9]+: *", "")
      case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
      case Incomplete => "error: incomplete expression"
    }
    outf(chan, result)
  }

  def restart(): Unit = {
    imain.foreach(_.close)
    imain = None
  }

  def receive = {
    case Repl.Eval(text) => eval(text)
    case Repl.Restart => restart()
  }
}
