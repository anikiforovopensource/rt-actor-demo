name := """rt-actor-demo"""

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.9",
  "com.typesafe.akka" %% "akka-actor" % "2.3.3",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.3",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3",
  "com.twitter" %% "util-core" % "6.16.0",
  "org.scalatest" % "scalatest_2.10" % "2.0" % "test")
