name := "RedisClient"

lazy val redisClient = (project in file(".")).settings(coreSettings : _*)

lazy val commonSettings: Seq[Setting[_]] = Seq(
  organization := "net.debasishg",
  version := "3.30",
  scalaVersion := "2.12.10",
  crossScalaVersions := Seq("2.12.11", "2.11.12", "2.10.7", "2.13.2"),

  scalacOptions in Compile ++= Seq( "-unchecked", "-feature", "-language:postfixOps", "-deprecation" ),

  resolvers ++= Seq(
    ("typesafe repo" at "http://repo.typesafe.com/typesafe/releases/").withAllowInsecureProtocol(true)
  )
)

def dockerTestKit(version: String): Seq[ModuleID] = {
  Seq("docker-testkit-scalatest", "docker-testkit-impl-docker-java").map("com.whisk" %% _ % version % Test) :+
    // https://github.com/eclipse-ee4j/jaxb-ri/issues/1222
    "javax.xml.bind" % "jaxb-api" % "2.3.1" % Test
}

lazy val coreSettings = commonSettings ++ Seq(
  name := "RedisClient",
  libraryDependencies ++= Seq(
    "org.apache.commons"      %  "commons-pool2"           % "2.8.0",
    "org.slf4j"               %  "slf4j-api"               % "1.7.30",
    "org.slf4j"               %  "slf4j-log4j12"           % "1.7.30"      % "provided",
    "log4j"                   %  "log4j"                   % "1.2.17"      % "provided",
    "org.scalatest"           %% "scalatest"               % "3.1.0"       % "test"
  ) ++
    (scalaBinaryVersion.value match {
      case "2.10" => dockerTestKit("0.9.8")
      case _ => dockerTestKit("0.9.9")
    })
  ,

  publishTo := version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }.value,
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
  unmanagedResources in Compile += baseDirectory.map( _ / "LICENSE" ).value
)
