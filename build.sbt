//
// Build file for recursive Akka stream example
//

name := "Interview1"

organization := "algorithmia"

// Allow version to be overwritten with "-DalgoVersion=XXX"
version := System.getProperty("algo.version", "1.0-SNAPSHOT")

scalaVersion := "2.12.8"

mainClass in Compile := Some("algorithmia.Main")

resolvers += "Maven Central" at "http://repo1.maven.org/maven2/org/"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.lihaoyi" %% "upickle" % "0.6.6",

  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.13" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

retrieveManaged := true

// Don't convert name to lowercase
normalizedName := name.value

