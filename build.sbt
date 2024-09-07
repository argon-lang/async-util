import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.linker.interface.ESVersion


val zioVersion = "2.0.21"

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
    scalaVersion := "3.4.2",

    organization := "dev.argon",
    version := "0.1.0-SNAPSHOT",


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

    name := "argon-async-util",
  )

