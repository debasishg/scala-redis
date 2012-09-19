import sbt._
import Keys._

object ScalaRedisProject extends Build
{
  import Resolvers._
  lazy val root = Project("RedisClient", file(".")) settings(coreSettings : _*)

  lazy val commonSettings: Seq[Setting[_]] = Seq(
    organization := "net.debasishg",
    version := "2.7-spaceape",
    scalaVersion := "2.9.2",
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    resolvers ++= Seq(twitterRepo)
  )

  publishMavenStyle := true

  lazy val coreSettings = commonSettings ++ Seq(
    name := "RedisClient",

    libraryDependencies ++= Seq("commons-pool" % "commons-pool" % "1.6",
      "org.slf4j"      % "slf4j-api"     % "1.6.6",
      "org.slf4j"      % "slf4j-log4j12" % "1.6.6"  % "provided",
      "log4j"          % "log4j"         % "1.2.16" % "provided",
      "junit"          % "junit"         % "4.8.1"  % "test",
      "org.scalatest"  % "scalatest_2.9.1" % "1.6.1" % "test",
      "com.twitter"    % "util_2.9.1"    % "1.12.13" % "test" intransitive(),
      "com.twitter"    % "finagle-core_2.9.1"  % "4.0.2" % "test"),

    parallelExecution in Test := false,
    publishTo <<= version { (v: String) =>
      Some("spaceape-publish" at "http://spaceapegames.artifactoryonline.com/spaceapegames/libs-releases-local/")
    },
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { repo => false },
    pomExtra := (
      <url>https://github.com/spaceapegames/scala-redis.git</url>
      <licenses>
        <license>
          <name>Apache 2.0 License</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:spaceapegames/scala-redis.git</url>
        <connection>scm:git:git@github.com:spaceapegames/scala-redis.git</connection>
      </scm>
      <developers>
        <developer>
          <id>spaceapegames</id>
          <name>SpaceApe</name>
          <url>http://www.spaceapegames.com</url>
        </developer>
      </developers>),
    unmanagedResources in Compile <+= baseDirectory map { _ / "LICENSE" }
  )
}

object Resolvers {
  val twitterRepo = "releases" at "http://spaceapegames.artifactoryonline.com/spaceapegames/libs-releases"
}
