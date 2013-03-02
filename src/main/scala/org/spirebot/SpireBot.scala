package org.spirebot

import org.jibble.pircbot.PircBot

import java.io.{PrintStream, ByteArrayOutputStream}

import scala.reflect.runtime.universe._
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results._
import scala.util.matching.Regex

object SpireBot extends PircBot {
  val name = "spirebot"

  val owners = Set("d_m")

  val channels = "#spire-math" :: Nil

  val imports = List(
    "scala.reflect.runtime.universe._",
    "spire.algebra._", "spire.implicits._", "spire.math._", "spire.random._", "spire.syntax._",
    "scalaz._",
    "shapeless._", "shapeless.contrib.spire._"
  )

  def main(args: Array[String]) {
    setName(name)
    setVerbose(true)
    setEncoding("UTF-8")
    connect()
  }

  def connect() {
    connect("irc.freenode.net")
    channels.foreach(joinChannel)
  }

  class ExitException() extends Exception("spirebot exiting...")

  override def onDisconnect: Unit = while (true) {
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
  
  val SystemOut = System.out
  val SystemErr = System.err
  val ConsoleOut = Console.out
  val ConsoleErr = Console.err

  val baos = new ByteArrayOutputStream
  val ps = new PrintStream(baos)

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

  val repls = scala.collection.mutable.Map[String, IMain]()

  val settings = {
    val settings = new scala.tools.nsc.Settings(s => System.err.println(s))
    settings.usejavacp.value = true
    settings.deprecation.value = true
    settings.feature.value = false
    settings.pluginsDir.value = "plugins"
    settings
  }

  def startRepl() = {
    val repl = new IMain(settings)
    imports.foreach(s => repl.quietImport(s))
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
      sendLines(channel, "reloaded interpreter")
    }

  def sendLines(channel: String, text: String) {
    val lines = text.filter(_ != '\r').split("\n").filter(! _.isEmpty)
    lines.take(5).foreach(s => sendMessage(channel, " " + s))
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
    useRepl(channel) { (repl, out) =>
      val result = repl.typeOfExpression(text).toString
      sendLines(channel, result)
    }
  }

  val Cmd = """^([^ ]+) *(.*)$""".r

  def handle(msg: Msg): Unit = msg.text match {
    case Cmd("!", expr) => eval(msg.channel, expr)
    case Cmd(":type", expr) => showType(msg.channel, expr)
    case Cmd(":show", expr) => eval(msg.channel, "showRaw(reify { %s }.tree)" format expr)
    case Cmd("@reload", _) => reload(msg.channel)
    case Cmd("@quit", _) => if (owners.contains(msg.sender)) throw new ExitException()
        
    case _ =>
  }
}
