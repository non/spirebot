import AssemblyKeys._

name := "spirebot"

version := "0.7"

scalaVersion := "2.10.3"

fork in run := true

resolvers += Resolver.sonatypeRepo("releases")

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies <++= (scalaVersion) { sv =>
  Seq(
    "org.scala-lang" % "scala-compiler" % sv,
    "org.scala-lang" % "scala-reflect" % sv,
    "com.typesafe.akka" %% "akka-actor" % "2.2.1",
    "org.spire-math" %% "spire" % "0.7.3",
    "com.chuusai" % "shapeless_2.10.2" % "2.0.0-M1",
    //"org.typelevel" %% "shapeless-spire" % "0.2-SNAPSHOT",
    "org.scalaz" %% "scalaz-core" % "7.0.3",
    //"org.typelevel" %% "shapeless-scalaz" % "0.2-SNAPSHOT",
    "org.typelevel" %% "scalaz-spire" % "0.2-SNAPSHOT",
    "ichi.bench" % "thyme" % "0.1.0" from "http://plastic-idolatry.com/jars/thyme-0.1.0.jar",
    "pircbot" % "pircbot" % "1.5.0"
  )
}

autoCompilerPlugins := true

assembleArtifact in packageBin := false

seq(assemblySettings: _*)

mergeStrategy in assembly <<= (mergeStrategy in assembly) { old =>
  {
    case "rootdoc.txt" => MergeStrategy.discard
    case "META-INF/MANIFEST.MF" => MergeStrategy.discard
    case _ => MergeStrategy.first
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
