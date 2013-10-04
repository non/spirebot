package spirebot

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef}
import org.jibble.pircbot.PircBot
import Util._

class Gateway(router: ActorRef) extends PircBot with Actor {

  def receive = {
    case Join(chan) => joinChannel(chan)
    case Leave(chan) => partChannel(chan)
    case Send(chan, text) => sendLines(chan, text)
    case Reconnect(ms) => reconnect(ms)
    case Quit => quit()
  }

  override def onPrivateMessage(sender: String, login: String, hostname: String, text: String) =
    router ! Msg(sender, sender, login, hostname, text)

  override def onMessage(channel: String, sender: String, login: String, hostname: String, text: String) =
    router ! Msg(channel, sender, login, hostname, text)

  val nick: String = setting("nick", "spirebot__")
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

  override def onDisconnect: Unit = if (!done) reconnect(1000)

  val system = context.system
  import system.dispatcher

  def reconnect(backoff: Int): Unit = try {
    connect()
  } catch {
    case e: Exception =>
      e.printStackTrace
      val n = math.min(backoff * 2, 600000)
      system.scheduler.scheduleOnce(n.millis, self, Reconnect(n))
  }

  def quit() {
    done = true
    disconnect()
    quitServer()
    context.stop(self)
  }

  def sendLines(channel: String, text: String) {
    val lines = text.replace("\r", "").split("\n").filter(_.nonEmpty)
    lines.take(5).foreach { s =>
      if (s.startsWith("/me ")) sendAction(channel, s.substring(4))
      else sendMessage(channel, s)
    }
  }
}
