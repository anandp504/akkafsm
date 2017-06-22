name in ThisBuild := "AkkaFSM"

organization in ThisBuild := "com.anand.akkafsm"

scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked"
  // "-Ywarn-unused-import" // turn on whenever you want to discover unused imports, 2.11+
  // "-Xfuture",
  // "-Xlint",
  // "-Ywarn-value-discard"
)

lazy val root = (project in file("."))
  .settings(
    name := "AkkaFSM",
    organization := "com.anand.akkafsm",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" % "akka-actor_2.11" % "2.5.3"
    )
  )
    