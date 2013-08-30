package org.spirebot

import org.jibble.pircbot.PircBot

import java.io.{PrintStream, ByteArrayOutputStream}

import scala.reflect.runtime.universe._
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results._
import scala.tools.reflect.ToolBox

import spire.implicits._
import spire.math._

import ichi.bench.Thyme

object Util {
  val th = new Thyme()

  def disp(t: Long): String =
    if (t < 1000) "%dns" format t
    else if (t < 1000000) "%.1fµs" format (t / 1000.0)
    else if (t < 1000000000) "%.2fms" format (t / 1000000.0)
    else "%.3fs" format (t / 1000000000.0)

  def fmt(n: Double): String = disp((n * 1000000000).toLong)

  def timer[A](f: => A): A = {
    val br = Thyme.Benched.empty
    val a = th.bench(f)(br, effort = 1)
    val (m, r, e) = (fmt(br.runtime), br.runtimeIterations, fmt(br.runtimeError))
    System.out.println(s"averaged $m over $r runs (± $e)")
    a
  }

  def unpack(e: Expr[_]) = e match {
    case Expr(Block(List(u), Literal(Constant(())))) => u
    case x => x.tree
  }
}

object Spirebot extends PircBot {

  def setting(name: String): Option[String] =
    Option(System.getProperty(name))
  def setting(name: String, default: String): String =
    setting(name).getOrElse(default)
  def parseInt(s: String): Option[Int] =
    if (s.matches("[0-9]+")) Some(s.toInt) else None

  val nick: String = setting("nick", "spirebot__")
  val owners: Set[String] = setting("owners", "d_m").split(",").toSet
  val channels: Seq[String] = setting("channels", "#spire-math").split(",")
  val server: String = setting("server", "irc.freenode.net")
  val port: Int = setting("port").flatMap(parseInt).getOrElse(6667)
  val password: Option[String] = setting("password")

  var done = false

  val SystemOut = System.out
  val SystemErr = System.err
  val ConsoleOut = Console.out
  val ConsoleErr = Console.err

  val baos = new ByteArrayOutputStream
  val ps = new PrintStream(baos, true, "UTF-8")

  val repls = scala.collection.mutable.Map[String, IMain]()

  val settings = {
    val settings = new scala.tools.nsc.Settings(s => System.err.println(s))
    settings.usejavacp.value = true
    settings.deprecation.value = true
    settings.feature.value = false
    settings.pluginsDir.value = "plugins"
    settings
  }

  val imports = List(
    "scalaz._", "Scalaz._",
    "shapeless._", "shapeless.contrib.spire._",
    "scala.reflect.runtime.universe._",
    "spire.algebra._", "spire.implicits._", "spire.math._", "spire.random._",
    "org.spirebot.Util._"
  )

  def main(args: Array[String]) {
    setName(nick)
    setVerbose(true)
    setEncoding("UTF-8")
    connect()
  }

  def connect() {
    password match {
      case Some(pass) => connect(server, port, pass)
      case None => connect(server, port)
    }
    channels.foreach(joinChannel)
  }

  class ExitException() extends Exception("spirebot exiting...")

  override def onDisconnect: Unit = while (!done) {
    try {
      connect()
      return
    } catch {
      case e: ExitException =>
        return ()
      case e: Exception =>
        e.printStackTrace
        Thread.sleep(10000)
    }
  }
  
  override def onPrivateMessage(sender: String, login: String, hostname: String, text: String) =
    onMessage(sender, sender, login, hostname, text)

  override def onMessage(channel: String, sender: String, login: String, hostname: String, text: String) =
    handle(Msg(channel, sender, login, hostname, text))
  
  case class Msg(channel: String, sender: String, login: String, hostname: String, text: String)
  
  def redirectOutputs() {
    System.setOut(ps)
    System.setErr(ps)
    Console.setOut(ps)
    Console.setErr(ps)
  }

  def resetOutputs() {
    System.setOut(SystemOut)
    System.setErr(SystemErr)
    Console.setOut(ConsoleOut)
    Console.setErr(ConsoleErr)
    ps.flush()
    baos.reset()
  }

  def captureOutput(block: => Unit) = try {
    redirectOutputs()
    block
  } finally {
    resetOutputs()
  }

  def startRepl() = {
    val repl = new IMain(settings)
    imports.foreach(repl.quietImport(_))
    repl
  }

  def useRepl(channel: String)(f: (IMain, ByteArrayOutputStream) => Unit) =
    this.synchronized {
      val repl = repls.getOrElseUpdate(channel, startRepl())
      captureOutput(f(repl, baos))
    }

  def reload(channel: String): Unit =
    this.synchronized {
      repls.remove(channel)
      sendAction(channel, "reloads the REPL")
    }

  def sendLines(channel: String, text: String) {
    val lines = text.filter(_ != '\r').split("\n").filter(! _.isEmpty)
    lines.take(5).foreach(s => sendMessage(channel, s))
  }

  def eval(channel: String, text: String) {
    useRepl(channel) { (repl, out) =>
      val result = repl.interpret(text) match {
        case Success => out.toString.replaceAll("^res[0-9]+: *", "")
        case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
        case Incomplete => "error: incomplete expression"
      }
      sendLines(channel, result)
    }
  }

  def showType(channel: String, text: String) {
    useRepl(channel) { (repl, _) =>
      sendLines(channel, repl.typeOfExpression(text).toString)
    }
  }

  def showTree(channel: String, text: String) {
    useRepl(channel) { (repl, out) =>
      repl.interpret(s"unpack(reify { $text })") match {
        case Success => sendLines(channel, out.toString.replaceAll("^[^=]* = *", "").replace(" >: Nothing <: Any", ""))
        case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
        case Incomplete => sendLines(channel, "error: incomplete expression")
      }
    }
  }

  def dumpTree(channel: String, text: String) {
    useRepl(channel) { (repl, out) =>
      repl.interpret(s"showRaw(unpack(reify { $text }))") match {
        case Success => sendLines(channel, out.toString.replaceAll("^[^=]* = *", ""))
        case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
        case Incomplete => sendLines(channel, "error: incomplete expression")
      }
    }
  }

  val helpMessage = """
! EXPR - evaluate EXPR in the repl (see also: @dump @show @time @type)
@help - show this message
@reload - reload the interpreter
""".trim

  def showHelp(channel: String): Unit = sendLines(channel, helpMessage)

  def quit() {
    done = true
    quitServer()
  }

  val Cmd = """^([^ ]+) *(.*)$""".r

  def handle(msg: Msg): Unit = msg.text match {
    case Cmd("!", expr) => eval(msg.channel, expr)
    case Cmd("@type", expr) => showType(msg.channel, expr)
    case Cmd("@show", expr) => showTree(msg.channel, expr)
    case Cmd("@dump", expr) => dumpTree(msg.channel, expr)
    case Cmd("@time", expr) => eval(msg.channel, s"timer { $expr }")
    case Cmd("@help", _) => showHelp(msg.channel)
    case Cmd("@reload", _) => reload(msg.channel)
    case Cmd("@quit", _) => if (owners.contains(msg.sender)) quit()
    case Cmd(s, _) if s.startsWith("@") || s.startsWith("!") => showHelp(msg.channel)
        
    case _ =>
  }
}
