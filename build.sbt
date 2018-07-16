//
// Algorithmia algorithm build file
//

name := "Interview1"

organization := "algorithmia"

// Allow version to be overwritten with "-DalgoVersion=XXX"
version := System.getProperty("algo.version", "1.0-SNAPSHOT")

scalaVersion := "2.11.11"

mainClass in Compile := Some("algorithmia.Main")

val repoUrl = System.getProperty("repo.url", "http://git.algorithmia.com")

resolvers += "Maven Central" at "http://repo1.maven.org/maven2/org/"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.algorithmia" % "algorithmia-client" % "1.0.+",
  "com.algorithmia" % "algorithmia-extras" % "1.0.+",
  "com.google.code.gson" % "gson" % "2.5",

  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-actor" % "2.5.13",
  "com.github.scopt" %% "scopt" % "3.7.0",
  "com.lihaoyi" %% "upickle" % "0.6.6",

  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.13" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

//libraryDependencies += "org.scalatest" % "scalatest_2.11" % "3.0.1" % "test"

retrieveManaged := true

// Don't convert name to lowercase
normalizedName := name.value

//assemblyMergeStrategy in assembly := {
//      case PathList("reference.conf") => MergeStrategy.concat
//}
