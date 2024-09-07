import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ESVersion


val zioVersion = "2.1.9"

publish / skip := true

ThisBuild / versionScheme := Some("semver-spec")
ThisBuild / credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  "3460F237EA4AEB29F91F0638133C9C282D54701F",
  "ignored",
)

lazy val root = crossProject(JVMPlatform, JSPlatform).in(file("."))
  .jsSettings(
    jsEnv := new NodeJSEnv(
      NodeJSEnv.Config()
    ),

    scalaJSLinkerConfig ~= {
      _
        .withModuleKind(ModuleKind.ESModule)
        .withBatchMode(true)
        .withESFeatures(_.withESVersion(ESVersion.ES2021))
    },
  )
  .settings(
    scalaVersion := "3.5.0",

    name := "Argon Async Util",
    organization := "dev.argon",
    version := "0.1.0",

    description := "Utilities for converting between different async models",
    homepage := Some(url("https://github.com/argon-lang/async-util")),

    licenses := Seq(
      "Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    ),

    scmInfo := Some(ScmInfo(
      connection = "scm:git:git@github.com:argon-lang/async-util.git",
      devConnection = "scm:git:git@github.com:argon-lang/async-util.git",
      browseUrl = url("https://github.com/argon-lang/async-util/tree/master"),
    )),

    pomExtra := (
      <developers>
        <developer>
          <name>argon-dev</name>
          <email>argon@argon.dev</email>
          <organization>argon-lang</organization>
          <organizationUrl>https://argon.dev</organizationUrl>
        </developer>
      </developers>
      ),

    publishTo := Some(MavenCache("target-repo", (Compile / target).value / "repo")),


    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-release", "22",
      "-source", "future",
      "-language:higherKinds",
      "-language:existentials",
      "-language:implicitConversions",
      "-language:strictEquality",
      "-deprecation",
      "-feature",
      "-Ycheck-all-patmat",
      "-Yretain-trees",
      "-Yexplicit-nulls",
      "-Xmax-inlines", "128",
      "-Wconf:id=E029:e,id=E165:e,id=E190:e,cat=unchecked:e,cat=deprecation:e",
    ),


    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio" % zioVersion,
      "dev.zio" %%% "zio-streams" % zioVersion,

      "dev.zio" %%% "zio-test" % zioVersion % "test",
      "dev.zio" %%% "zio-test-sbt" % zioVersion % "test",
    ),

  )

