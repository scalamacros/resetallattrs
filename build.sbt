import sbt._
import Keys._

lazy val sharedSettings = Seq(
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8", "2.12.7", "2.13.0"),
  crossVersion := CrossVersion.binary,
  version := "1.0.0-SNAPSHOT",
  organization := "org.scalamacros",
  description := "The missing compatibility library for reflection in Scala 2.11/2.12/2.13 when c.untypecheck is not enough",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  publishMavenStyle := true,
  publishArtifact in Compile := false,
  publishArtifact in Test := false,
  scalacOptions ++= Seq("-deprecation", "-feature") ++ { scalaVersion.value match {
    case v if (v startsWith "2.12") || (v startsWith "2.13") =>
      // Workaround for https://github.com/scala/bug/issues/10717, should be fixed in 2.12.7, 2.13.0-M4 --TG
      Seq("-opt:l:inline", "-opt-inline-from:**")

    case _ => Seq("-optimise")
  } },
  parallelExecution in Test := false, // hello, reflection sync!!
  logBuffered := false,
  scalaHome := {
    val scalaHome = System.getProperty("resetallattrs.scala.home")
    if (scalaHome != null) {
      println(s"Going for custom scala home at $scalaHome")
      Some(file(scalaHome))
    } else None
  },
  publishArtifact in Compile := false,
  publishArtifact in Test := false,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  pomIncludeRepository := { x => false },
  pomExtra := (
    <url>https://github.com/scalamacros/resetallattrs</url>
    <inceptionYear>2014</inceptionYear>
    <licenses>
      <license>
        <name>BSD-like</name>
        <url>http://www.scala-lang.org/downloads/license.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git://github.com/scalamacros/resetallattrs.git</url>
      <connection>scm:git:git://github.com/scalamacros/resetallattrs.git</connection>
    </scm>
    <issueManagement>
      <system>GitHub</system>
      <url>https://github.com/scalamacros/resetallattrs/issues</url>
    </issueManagement>
    <developers>
      <developer>
        <id>xeno-by</id>
        <name>Eugene Burmako</name>
        <url>http://xeno.by</url>
      </developer>
    </developers>
  )
)

// http://stackoverflow.com/questions/20665007/how-to-publish-only-when-on-master-branch-under-travis-and-sbt-0-13
val publishOnlyWhenOnMaster = taskKey[Unit]("publish task for Travis (don't publish when building pull requests, only publish when the build is triggered by merge into master)")
def publishOnlyWhenOnMasterImpl = Def.taskDyn {
  import scala.util.Try
  val travis   = Try(sys.env("TRAVIS")).getOrElse("false") == "true"
  val pr       = Try(sys.env("TRAVIS_PULL_REQUEST")).getOrElse("false") != "false"
  val branch   = Try(sys.env("TRAVIS_BRANCH")).getOrElse("??")
  val snapshot = version.value.trim.endsWith("SNAPSHOT")
  (travis, pr, branch, snapshot) match {
    case (true, false, "master", true) => publish
    case _                             => Def.task {}
  }
}

lazy val publishableSettings = sharedSettings ++ Seq(
  publishOnlyWhenOnMaster := publishOnlyWhenOnMasterImpl.value,
  publishArtifact in Compile := true,
  publishArtifact in Test := false,
  credentials ++= {
    val mavenSettingsFile = System.getProperty("maven.settings.file")
    if (mavenSettingsFile != null) {
      println("Loading Sonatype credentials from " + mavenSettingsFile)
      try {
        import scala.xml._
        val settings = XML.loadFile(mavenSettingsFile)
        def readServerConfig(key: String) = (settings \\ "settings" \\ "servers" \\ "server" \\ key).head.text
        List(Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          readServerConfig("username"),
          readServerConfig("password")
        ))
      } catch {
        case ex: Exception =>
          println("Failed to load Maven settings from " + mavenSettingsFile + ": " + ex)
          Nil
      }
    } else {
      (for {
        realm <- sys.env.get("SCALAMACROS_MAVEN_REALM")
        domain <- sys.env.get("SCALAMACROS_MAVEN_DOMAIN")
        user <- sys.env.get("SCALAMACROS_MAVEN_USER")
        password <- sys.env.get("SCALAMACROS_MAVEN_PASSWORD")
      } yield {
        println("Loading Sonatype credentials from environment variables")
        Credentials(realm, domain, user, password)
      }).toList
    }
  }
)

lazy val root = Project(
  id = "root",
  base = file("root")
) settings (
  sharedSettings : _*
) settings (
  test in Test := (test in tests in Test).value,
  packagedArtifacts := Map.empty
) aggregate (resetallattrs, tests)

lazy val resetallattrs = Project(
  id   = "resetallattrs",
  base = file("resetallattrs")
) settings (
  publishableSettings: _*
) settings (
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
  scalacOptions ++= Seq()
)

lazy val tests = Project(
  id   = "tests",
  base = file("tests")
) settings (
  sharedSettings: _*
) settings (
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
  scalacOptions ++= Seq(),
  packagedArtifacts := Map.empty
) dependsOn (resetallattrs)
