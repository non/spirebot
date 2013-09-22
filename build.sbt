import AssemblyKeys._

name := "spirebot"

version := "0.5"

scalaVersion := "2.10.2"

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies <++= (scalaVersion) { sv =>
  Seq(
    // scala
    "org.scala-lang" % "scala-compiler" % sv,
    "org.scala-lang" % "scala-reflect" % sv,
    // akka
    "com.typesafe.akka" %% "akka-actor" % "2.2.1",
    // spire
    "org.spire-math" %% "spire" % "0.6.1",
    // shapeless
    "com.chuusai" %% "shapeless" % "1.2.4",
    "org.typelevel" %% "shapeless-spire" % "0.2-SNAPSHOT",
    // scalaz
    "org.scalaz" %% "scalaz-core" % "7.0.3",
    "org.typelevel" %% "shapeless-scalaz" % "0.2-SNAPSHOT",
    "org.typelevel" %% "scalaz-spire" % "0.2-SNAPSHOT",
    // thyme
    "ichi.bench" % "thyme" % "0.1.0" from "http://plastic-idolatry.com/jars/thyme-0.1.0.jar",
    // irc
    "pircbot" % "pircbot" % "1.5.0"
  )
}

autoCompilerPlugins := true

assembleArtifact in packageBin := false

seq(assemblySettings: _*)

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
  {
    case "rootdoc.txt" => MergeStrategy.discard
    case x => old(x)
  }
}

scalacOptions ++= Seq("-feature", "-language:_", "-deprecation", "-Xexperimental")

// conflictWarning ~= { cw =>
//   cw.copy(
//     filter = (id: ModuleID) => id.organization != "org.scala-lang",
//     group = (id: ModuleID) => id.organization + ":" + id.name,
//     level = Level.Error, failOnConflict = true
//   )
// }
