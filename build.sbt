import AssemblyKeys._

name := "spirebot"

version := "0.1"

scalaVersion := "2.10.0"

libraryDependencies ++= {
  Seq(
    // irc
    "pircbot" % "pircbot" % "1.5.0",
    // scala
    "org.scala-lang" % "scala-compiler" % "2.10.0",
    "org.scala-lang" % "scala-reflect" % "2.10.0",
    // spire
    "org.spire-math" %% "spire" % "0.3.0",
    // shapeless
    "com.chuusai" %% "shapeless" % "1.2.4",
    "org.typelevel" %% "shapeless-spire" % "0.1",
    // scalaz
    "org.scalaz" %% "scalaz-core" % "7.0.0-M8",
    "org.typelevel" %% "shapeless-scalaz" % "0.1",
    "org.typelevel" %% "scalaz-spire" % "0.1.1"
  )
}

autoCompilerPlugins := true

assembleArtifact in packageBin := false

seq(assemblySettings: _*)

scalacOptions ++= Seq("-feature", "-language:_", "-deprecation", "-Xexperimental")

conflictWarning ~= { cw =>
  cw.copy(filter = (id: ModuleID) => true, group = (id: ModuleID) => id.organization + ":" + id.name, level = Level.Error, failOnConflict = true)
}
