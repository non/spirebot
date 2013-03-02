package org.spirebot

import org.jibble.pircbot.PircBot

import java.io.{PrintStream, ByteArrayOutputStream}

import scala.reflect.runtime.universe._
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results._
import scala.util.matching.Regex

import spire.implicits._
import spire.math._
import spire.syntax._

object Timer {
  def timer[A](f: => A): A = {
    val timerRuns = 5

    def disp(t: Long): String = if (t < 1000) {
      "%dns" format t
    } else if (t < 1000000) {
      "%.1fµs" format (t / 1000.0)
    } else {
      "%.2fms" format (t / 1000000.0)
    }

    var ts: List[Long] = Nil
    var a: A = f
    cfor(0)(_ < timerRuns, _ + 1) { i =>
      val t0 = System.nanoTime()
      a = f
      val t = System.nanoTime() - t0
      ts = t :: ts
    }
    val mean = ts.qsum / timerRuns
    val dev = (ts.map(t => pow(t - mean, 2)).qsum / timerRuns).sqrt

    System.out.println("mean %s over %d runs (± %s)" format (disp(mean), timerRuns, disp(dev)))
    a
  }
}

object Spirebot extends PircBot {
  def setting(name: String, default: String): String =
    Option(System.getProperty(name)).getOrElse(default)

  val nick: String = setting("nick", "spirebot__")

  val owners: Set[String] = setting("owners", "d_m").split(",").toSet

  val channels: Seq[String] = setting("channels", "#spire-math").split(",")

  var done = false

  val SystemOut = System.out
  val SystemErr = System.err
  val ConsoleOut = Console.out
  val ConsoleErr = Console.err

  val baos = new ByteArrayOutputStream
  val ps = new PrintStream(baos)

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
    "spire.algebra._", "spire.implicits._", "spire.math._", "spire.random._", "spire.syntax._"
  )

  val defines = List(
    """
  def timer[A](f: => A): A = {
    val timerRuns = 5

    def disp(t: Long): String = if (t < 1000) {
      "%dns" format t
    } else if (t < 1000000) {
      "%.1fµs" format (t / 1000.0)
    } else {
      "%.2fms" format (t / 1000000.0)
    }

    var ts: List[Long] = Nil
    var a: A = f
    cfor(0)(_ < timerRuns, _ + 1) { i =>
      val t0 = System.nanoTime()
      a = f
      val t = System.nanoTime() - t0
      ts = t :: ts
    }
    val mean = ts.qsum / timerRuns
    val dev = (ts.map(t => pow(t - mean, 2)).qsum / timerRuns).sqrt

    System.out.println("averaged %s over %d runs (± %s)" format (disp(mean), timerRuns, disp(dev)))
    a
  }
""".trim
  )

  def main(args: Array[String]) {
    setName(nick)
    setVerbose(true)
    setEncoding("UTF-8")
    connect()
  }

  def connect() {
    //connect("irc.my.server.org", 23045, "mypassword")
    connect("irc.freenode.net")
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
    defines.foreach(repl.interpret(_))
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
        case Success => out.toString.replaceAll("(?m:^res[0-9]+: *)", "")
        case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
        case Incomplete => "error: unexpected EOF found, incomplete expression"
      }
      sendLines(channel, result)
    }
  }

  def showType(channel: String, text: String) {
    useRepl(channel) { (repl, _) =>
      sendLines(channel, repl.typeOfExpression(text).toString)
    }
  }

  val helpMessage = """
! EXPR - evaluate EXPR in the repl (see also: @type @show @time)
@help - show this message
@reload - reload the interpreter
""".trim

  def showHelp(channel: String) {
    sendLines(channel, helpMessage)
  }

  def quit() {
    done = true
    quitServer()
  }

  val Cmd = """^([^ ]+) *(.*)$""".r

  def handle(msg: Msg): Unit = msg.text match {
    case Cmd("!", expr) => eval(msg.channel, expr)
    case Cmd("@type", expr) => showType(msg.channel, expr)
    case Cmd("@show", expr) => eval(msg.channel, "showRaw(reify { %s }.tree)" format expr)
    case Cmd("@time", expr) => eval(msg.channel, "timer { %s }" format expr)
    case Cmd("@help", _) => showHelp(msg.channel)
    case Cmd("@reload", _) => reload(msg.channel)
    case Cmd("@quit", _) => if (owners.contains(msg.sender)) quit()
    case Cmd(s, _) if s.startsWith("@") || s.startsWith("!") => showHelp(msg.channel)
        
    case _ =>
  }
}
