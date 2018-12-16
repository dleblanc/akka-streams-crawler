//
// Build file for recursive Akka stream example
//

name := "Interview1"

organization := "dml"

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.8"

mainClass in Compile := Some("dml.interview1.StreamBasedCrawler")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.lihaoyi" %% "upickle" % "0.6.6",

  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.13" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)
