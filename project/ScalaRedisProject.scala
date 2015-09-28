import sbt._
import Keys._

object ScalaRedisProject extends Build
{
  import Resolvers._
  lazy val root = Project("RedisClient", file(".")) settings(coreSettings : _*)

  lazy val commonSettings: Seq[Setting[_]] = Seq(
    organization := "net.debasishg",
    version := "3.1",
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.11.6", "2.10.4"),

    scalacOptions in Compile ++= Seq( "-unchecked", "-feature", "-language:postfixOps", "-deprecation" ),

    resolvers ++= Seq(akkaRepo)
  )

  lazy val coreSettings = commonSettings ++ Seq(
    name := "RedisClient",
    libraryDependencies := Seq(
      "commons-pool"      %  "commons-pool"            % "1.6",
      "org.slf4j"         %  "slf4j-api"               % "1.7.2",
      "org.slf4j"         %  "slf4j-log4j12"           % "1.7.2"      % "provided",
      "log4j"             %  "log4j"                   % "1.2.16"     % "provided",
      "com.typesafe.akka" %% "akka-actor"              % "2.3.6",
      "com.kohlschutter.junixsocket" % "junixsocket-native-common" % "2.0.4",
      "junit"             %  "junit"                   % "4.8.1"      % "test",
      "org.scalatest"     %%  "scalatest"              % "2.1.3" % "test"),

    parallelExecution in Test := false,
    publishTo <<= version { (v: String) => 
      val nexus = "https://oss.sonatype.org/" 
      if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2") 
    },
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype.credentials"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { repo => false },
    pomExtra := (
      <url>https://github.com/debasishg/scala-redis</url>
      <licenses>
        <license>
          <name>Apache 2.0 License</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:debasishg/scala-redis.git</url>
        <connection>scm:git:git@github.com:debasishg/scala-redis.git</connection>
      </scm>
      <developers>
        <developer>
          <id>debasishg</id>
          <name>Debasish Ghosh</name>
          <url>http://debasishg.blogspot.com</url>
        </developer>
      </developers>),
    unmanagedResources in Compile <+= baseDirectory map { _ / "LICENSE" }
  )
}

object Resolvers {
  val akkaRepo = "typesafe repo" at "http://repo.typesafe.com/typesafe/releases/"
}
