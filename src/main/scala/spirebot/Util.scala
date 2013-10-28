package spirebot

import scala.reflect.runtime.universe._

import ichi.bench.Thyme

object Util {
  val th = new Thyme(watchLoads = false, watchGarbage = false, watchMemory = false)

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

  def setting(name: String): Option[String] =
    Option(System.getProperty(name))

  def setting(name: String, default: String): String =
    setting(name).getOrElse(default)

  def parseInt(s: String): Option[Int] =
    if (s.matches("[0-9]+")) Some(s.toInt) else None

  def unpack(e: Expr[_]) = e match {
    case Expr(Block(List(u), Literal(Constant(())))) => u
    case x => x.tree
  }

  def dump(e: Expr[_]) = showRaw(unpack(e))
}
