package spirebot

case object Quit
case class Join(chan: String)
case class Leave(chan: String)
case class Msg(channel: String, sender: String, login: String, hostname: String, text: String)
case class Send(chan: String, text: String)
case class Eval(s: String)
case class Benchmark(s: String)
case class TimeEval(s: String)
case class ShowType(s: String)
case class ShowTree(s: String)
case class DumpTree(s: String)
case class Reconnect(ms: Int)
case object Reload
case object Tick

