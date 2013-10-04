package spirebot

import java.io.{PrintStream, ByteArrayOutputStream}
import scala.concurrent.duration._
import scala.reflect.runtime.universe._
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results._
import scala.tools.nsc.Settings
import akka.actor.{Actor, ActorRef}
import Util._

class Repl(chan: String, gateway: ActorRef, imports: Seq[String]) extends Actor {

  var lastActive: Long = 0L

  def receive = {
    case Eval(s) => eval(s)
    case ShowType(s) => showType(s)
    case ShowTree(s) => showTree(s)
    case DumpTree(s) => dumpTree(s)
    case Benchmark(s) => eval(s"spirebot.Util.timer { $s }")
    case Reload => reload()
    case Tick => closeIfIdle()
    case Quit => context.stop(self)
  }

  def reload(): Unit = {
    imain.foreach(_.close)
    imain = None
  }

  def eval(text: String): Unit = use { (im, out) =>
    im.interpret(text) match {
      case Success => out.toString.replaceAll("^res[0-9]+: *", "")
      case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
      case Incomplete => "error: incomplete expression"
    }
  }

  def showType(text: String): Unit = use { (im, out) =>
    im.typeOfExpression(text).toString
  }

  def showTree(text: String): Unit = use { (im, out) =>
    im.interpret(s"unpack(reify { $text })") match {
      case Success => out.toString.replaceAll("^[^=]* = *", "").replace(" >: Nothing <: Any", "")
      case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
      case Incomplete => "error: incomplete expression"
    }
  }

  def dumpTree(text: String): Unit = use { (im, out) =>
    im.interpret(s"showRaw(unpack(reify { $text }))") match {
      case Success => out.toString.replaceAll("^[^=]* = *", "")
      case Error => out.toString.replaceAll("^<console>:[0-9]+: *", "")
      case Incomplete => "error: incomplete expression"
    }
  }

  val timeout = 1000 * 60 * 60

  val system = context.system
  import system.dispatcher

  override def preStart() =
    system.scheduler.scheduleOnce(60.seconds, self, Tick)

  def closeIfIdle(): Unit = {
    imain.foreach { im =>
      val now = System.currentTimeMillis
      if (now - lastActive >= timeout) {
        imain = None
        im.close()
      }
    }
    system.scheduler.scheduleOnce(60.seconds, self, Tick)
  }

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

  def use(f: (IMain, ByteArrayOutputStream) => String): Unit = {
    lastActive = System.currentTimeMillis
    val im = imain.getOrElse(startIMain)
    imain = Some(im)
    captureOutput(f(im, baos))
  }

  def captureOutput(block: => String): Unit = {
    val s = try {
      redirectOutputs()
      block
    } finally {
      resetOutputs()
    }
    gateway ! Send(chan, s)
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
}
