ThisBuild / organization := "bot.poll.simple"

ThisBuild / scalaVersion := "2.13.5"

ThisBuild / version := "0.0.1-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq( "-feature" )

ThisBuild / exportJars := true

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:higherKinds"
) ++ (CrossVersion.partialVersion( (ThisBuild / scalaVersion).value ) match {
  //case Some( ( 2, n ) ) if n >= 13 => Seq( "-Xsource:2.14" )
  case Some( ( 2, n ) ) if n < 13 => Seq( "-Ypartial-unification" )
  case _                          => Seq()
})

ThisBuild / javacOptions ++= Seq(
  "-Xlint:deprecation",
  "-source",
  "1.8",
  "-target",
  "1.8",
  "-Xlint"
)

// Required dependencies
val slackMorphismVersion = "3.1.0"

// This template is for akka and http4s as a primary framework
val http4sVersion = "0.21.1"
val sttpVersion = "2.0.6" // for STTP for http4s

// logging and configs for example
val logbackVersion = "1.2.3"
val scalaLoggingVersion = "3.9.2"
val declineVersion = "1.0.0"

// To provide a ready to work example, we're using in this template embedded SwayDb to store tokens
// You should consider to use more appropriate solutions depends on your requirements
val swayDbVersion = "0.11"

val kindProjectorVer = "0.11.3"

lazy val compilerPluginSettings = Seq(
  addCompilerPlugin( "org.typelevel" % "kind-projector" % kindProjectorVer cross CrossVersion.full )
)

lazy val root =
  (project in file( "." ))
    .settings(
      name := "simple-poll-bot",
      libraryDependencies ++= Seq(
        "ch.qos.logback" % "logback-classic" % logbackVersion,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
        "org.http4s" %% "http4s-blaze-server" % http4sVersion,
        "org.http4s" %% "http4s-blaze-client" % http4sVersion,
        "org.http4s" %% "http4s-circe" % http4sVersion,
        "org.http4s" %% "http4s-dsl" % http4sVersion,
        "com.softwaremill.sttp.client" %% "http4s-backend" % sttpVersion,
        "org.latestbit" %% "slack-morphism-client" % slackMorphismVersion,
        "com.monovore" %% "decline" % declineVersion
          exclude ("org.typelevel", "cats-core"),
        "com.monovore" %% "decline-effect" % declineVersion
          exclude ("org.typelevel", "cats-core")
          exclude ("org.typelevel", "cats-effect"),
        "io.swaydb" %% "swaydb" % swayDbVersion
          excludeAll (
            ExclusionRule( organization = "org.scala-lang.modules" ),
            ExclusionRule( organization = "org.reactivestreams" )
        ),
        "io.swaydb" %% "cats-effect" % swayDbVersion
          excludeAll (
            ExclusionRule( organization = "org.scala-lang.modules" ),
            ExclusionRule( organization = "org.reactivestreams" ),
            ExclusionRule( organization = "org.typelevel" )
        )
      )
    )
    .settings( compilerPluginSettings )

// Those just have better UX with sbt run and Ctrl-C, so remove them if you don't need it
ThisBuild / fork in run := true
Global / cancelable := false
