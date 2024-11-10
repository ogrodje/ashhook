import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import Dependencies.*
import NativePackagerHelper._

ThisBuild / version      := "0.0.1"
ThisBuild / scalaVersion := "3.6.1"
// ThisBuild / scalaVersion := "2.13.15"

ThisBuild / evictionErrorLevel := Level.Info

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(name := "ashhook")
  .settings(
    Compile / mainClass := Some("si.ogrodje.ashhook.Main"),
    libraryDependencies ++= zio ++ mail,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-release:21"
    )
  )
  .settings(
    assembly / mainClass             := Some("si.ogrodje.ashhook.Main"),
    assembly / assemblyJarName       := "ashhook.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")                        => MergeStrategy.discard
      case PathList("META-INF", "jpms.args")                    => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("META-INF", "javamail.providers")           => MergeStrategy.first
      case PathList("deriving.conf")                            => MergeStrategy.last
      case PathList(ps @ _*) if ps.last endsWith ".class"       => MergeStrategy.last
      case x                                                    =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
  .settings(
    Compile / mainClass             := Some("si.ogrodje.ashhook.Main"),
    Compile / discoveredMainClasses := Seq(),
    dockerExposedPorts              := Seq(8866),
    dockerExposedUdpPorts           := Seq.empty[Int],
    dockerUsername                  := Some("ogrodje"),
    dockerUpdateLatest              := true,
    dockerRepository                := Some("ghcr.io"),
    dockerBaseImage                 := "azul/zulu-openjdk-alpine:21-latest",
    packageName                     := "ashhook",
//    Docker / dockerPackageMappings += (
//      baseDirectory.value / "players.yml"
//    )                               -> "/opt/docker/players.yml",
    dockerCommands                  := dockerCommands.value.flatMap {
      case add @ Cmd("RUN", args @ _*) if args.contains("id") =>
        List(
          Cmd("LABEL", "maintainer Oto Brglez <otobrglez@gmail.com>"),
          Cmd("LABEL", "org.opencontainers.image.url https://github.com/ogrodje/ashhook"),
          Cmd("LABEL", "org.opencontainers.image.source https://github.com/ogrodje/ashhook"),
          Cmd("RUN", "apk add --no-cache bash"),
          Cmd("ENV", "SBT_VERSION", sbtVersion.value),
          Cmd("ENV", "SCALA_VERSION", scalaVersion.value),
          Cmd("ENV", "ASHHOOK_VERSION", version.value),
          add
        )
      case other                                              => List(other)
    }
  )

// resolvers ++= Resolver.sonatypeOssRepos("snapshots")

resolvers ++= Dependencies.projectResolvers ++
  Resolver.sonatypeOssRepos("snapshots")
