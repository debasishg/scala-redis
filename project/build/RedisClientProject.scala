import sbt._
import sbt.CompileOrder._

class RedisClientProject(info: ProjectInfo) extends DefaultProject(info) 
{
  // override def useDefaultConfigurations = true
  override def compileOptions = super.compileOptions ++
    Seq("-deprecation", "-Xcheckinit", "-encoding", "utf8").map(x => CompileOption(x))


  val scalaToolsSnapshots = "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"
  val scalaToolsReleases = "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases"
  val scalatest = "org.scalatest" % "scalatest_2.9.0" % "1.4.1" % "test"
  val junit = "junit" % "junit" % "4.8.1"
  val log4j = "log4j" % "log4j" % "1.2.16"
  val slf4japi = "org.slf4j" % "slf4j-api" % "1.5.8"
  val slf4j = "org.slf4j" % "slf4j-log4j12" % "1.5.8"
  val commons_pool = "commons-pool" % "commons-pool" % "1.5.5" % "compile" //ApacheV2

  override def packageSrcJar = defaultJarPath("-sources.jar")
  lazy val sourceArtifact = Artifact.sources(artifactID)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)

  override def managedStyle = ManagedStyle.Maven
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  lazy val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
}
