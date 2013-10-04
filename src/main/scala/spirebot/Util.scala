package spirebot

object Util {
  def setting(name: String): Option[String] =
    Option(System.getProperty(name))
  def setting(name: String, default: String): String =
    setting(name).getOrElse(default)
  def parseInt(s: String): Option[Int] =
    if (s.matches("[0-9]+")) Some(s.toInt) else None
}
