import sbt._
import sbt.CompileOrder._

class RedisClientProject(info: ProjectInfo) extends DefaultProject(info) 
{
  // override def useDefaultConfigurations = true
  override def compileOptions = super.compileOptions ++
    Seq("-deprecation", "-Xcheckinit", "-Xwarninit", "-encoding", "utf8").map(x => CompileOption(x))


  val scalaToolsSnapshots = "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"
  val scalaToolsReleases = "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases"
  val scalatest =
    buildScalaVersion match {
      case "2.7.7" => 
        "org.scalatest" % "scalatest" % "1.0" 
      case "2.8.0.RC3" =>
        "org.scalatest" % "scalatest" % "1.2-for-scala-2.8.0.RC3-SNAPSHOT" % "test"
      case "2.8.0.RC7" =>
        "org.scalatest" % "scalatest" % "1.2-for-scala-2.8.0.RC6-SNAPSHOT" % "test"
      case "2.8.0" | "2.8.1" =>
        "org.scalatest" % "scalatest" % "1.2-for-scala-2.8.0.final-SNAPSHOT" % "test"
    }
  val junit = "junit" % "junit" % "4.8.1"
  val log4j = "log4j" % "log4j" % "1.2.16"
  val slf4japi = "org.slf4j" % "slf4j-api" % "1.5.8"
  val slf4j = "org.slf4j" % "slf4j-log4j12" % "1.5.8"

  override def packageSrcJar = defaultJarPath("-sources.jar")
  lazy val sourceArtifact = Artifact.sources(artifactID)
  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)

  override def managedStyle = ManagedStyle.Maven
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  lazy val publishTo = "Scala Tools Nexus" at "http://maven/content/repositories/thirdparty/"
}
