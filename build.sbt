import AssemblyKeys._

name := "spirebot"

version := "0.2"

scalaVersion := "2.10.1"

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies <++= (scalaVersion) { sv =>
  Seq(
    // scala
    "org.scala-lang" % "scala-compiler" % sv,
    "org.scala-lang" % "scala-reflect" % sv,
    // spire
    "org.spire-math" %% "spire" % "0.4.0-M3",
    // shapeless
    "com.chuusai" %% "shapeless" % "1.2.4",
    "org.typelevel" %% "shapeless-spire" % "0.1.1",
    // scalaz
    "org.scalaz" %% "scalaz-core" % "7.0.0",
    "org.typelevel" %% "shapeless-scalaz" % "0.1.1",
    "org.typelevel" %% "scalaz-spire" % "0.1.4",
    // irc
    "pircbot" % "pircbot" % "1.5.0"
  )
}

autoCompilerPlugins := true

assembleArtifact in packageBin := false

seq(assemblySettings: _*)

scalacOptions ++= Seq("-feature", "-language:_", "-deprecation", "-Xexperimental")

conflictWarning ~= { cw =>
  cw.copy(
    filter = (id: ModuleID) => id.organization != "org.scala-lang",
    group = (id: ModuleID) => id.organization + ":" + id.name,
    level = Level.Error, failOnConflict = true
  )
}
