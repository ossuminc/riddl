/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.sbt.plugin

import com.ossuminc.riddl.sbt.SbtRiddlPluginBuildInfo
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

import java.io.File
import scala.sys.process._
import scala.util.matching.Regex

/** An sbt plugin that provides riddlc commands with
  * auto-acquisition from GitHub releases, batch operations
  * over multiple .conf files, and pre-compile validation.
  */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    // --- Binary resolution ---

    val riddlcPath = settingKey[Option[File]](
      "Explicit path to riddlc binary (overrides download)"
    )
    val riddlcVersion = settingKey[String](
      "Version of riddlc to download from GitHub releases"
    )
    val riddlcCacheDir = settingKey[File](
      "Cache directory for downloaded riddlc binaries"
    )
    val riddlcMinVersion = settingKey[String](
      "Minimum required riddlc version"
    )
    val riddlcOptions = settingKey[Seq[String]](
      "Global options to pass to riddlc"
    )

    // --- Source configuration ---

    val riddlcConf = settingKey[File](
      "Path to primary riddlc config file (single-conf mode)"
    )
    val riddlcSourceDir = settingKey[File](
      "Base directory for RIDDL .conf file scanning"
    )
    val riddlcConfExclusions = settingKey[Seq[String]](
      "Directory names to exclude from .conf scanning"
    )
    val riddlcValidateOnCompile = settingKey[Boolean](
      "Run riddlc validation during compile"
    )

    // --- Tasks ---

    val riddlcDownload = taskKey[File](
      "Download and cache riddlc from GitHub releases"
    )
    val riddlcBinary = taskKey[File](
      "Resolve riddlc binary (explicit, download, or PATH)"
    )
    val riddlcValidate = taskKey[Unit](
      "Validate RIDDL models"
    )
    val riddlcParse = taskKey[Unit](
      "Parse RIDDL models without validation"
    )
    val riddlcBastify = taskKey[Unit](
      "Generate .bast files from RIDDL models"
    )
    val riddlcPrettify = taskKey[Unit](
      "Reformat RIDDL source files"
    )
    val riddlcInfo = taskKey[Unit](
      "Display riddlc build information"
    )
    val riddlcShowVersion = taskKey[Unit](
      "Display riddlc version string"
    )

    /** Curried configuration function for convenient plugin
      * setup. Use with `.configure(riddlc())` on a Project.
      */
    def riddlc(
      version: String = SbtRiddlPluginBuildInfo.version,
      sourceDir: String = "src/main/riddl",
      validateOnCompile: Boolean = true,
      confExclusions: Seq[String] = Seq("patterns"),
      options: Seq[String] = Seq("--show-times")
    )(project: Project): Project = {
      val base = Seq(
        riddlcVersion := version,
        riddlcSourceDir := baseDirectory.value / sourceDir,
        riddlcValidateOnCompile := validateOnCompile,
        riddlcConfExclusions := confExclusions,
        riddlcOptions := options
      )
      val hook = if (validateOnCompile) Seq(
        Compile / compile := Def.taskDyn {
          Def.task {
            riddlcValidate.value
            (Compile / compile).value
          }
        }.value
      ) else Seq.empty
      project.settings(base ++ hook)
    }
  }

  import autoImport._

  // --- Input file extraction ---
  // Note: Methods are private[plugin] rather than private so
  // that the Scala 2.12 compiler can see usage through sbt
  // macro-generated task bodies.

  private[plugin] val InputFilePattern: Regex =
    """input-file\s*=\s*"([^"]+)"""".r

  private[plugin] def extractInputFile(
    conf: File
  ): Option[String] = {
    val content = IO.read(conf)
    InputFilePattern.findFirstMatchIn(content).map(_.group(1))
  }

  // --- Platform detection & download ---

  private[plugin] def platformAssetName: String = {
    val osName = sys.props.getOrElse("os.name", "").toLowerCase
    val osArch = sys.props.getOrElse("os.arch", "").toLowerCase
    if (osName.contains("mac") &&
        (osArch.contains("aarch64") ||
         osArch.contains("arm64"))) {
      "riddlc-macos-arm64.zip"
    } else if (osName.contains("linux") &&
               (osArch.contains("amd64") ||
                osArch.contains("x86_64"))) {
      "riddlc-linux-x86_64.zip"
    } else {
      "riddlc.zip" // JVM universal fallback
    }
  }

  private[plugin] def downloadRiddlc(
    cacheDir: File,
    version: String,
    log: Logger
  ): File = {
    val versionDir = cacheDir / version
    val binary = versionDir / "bin" / "riddlc"

    if (!binary.exists()) {
      val assetName = platformAssetName
      log.info(
        s"Downloading riddlc $version ($assetName)..."
      )
      IO.createDirectory(versionDir)
      val zipFile = versionDir / assetName
      val url =
        "https://github.com/ossuminc/riddl/releases/" +
        s"download/$version/$assetName"
      Process(Seq(
        "curl", "-fSL", "-o",
        zipFile.getAbsolutePath, url
      )).!!
      IO.unzip(zipFile, versionDir)
      binary.setExecutable(true)
      IO.delete(zipFile)
      log.info(
        s"riddlc $version cached at " +
        binary.getAbsolutePath
      )
    } else {
      log.debug(
        s"Using cached riddlc $version at " +
        binary.getAbsolutePath
      )
    }

    binary
  }

  // --- PATH lookup ---

  private[plugin] def findOnPath: Option[File] = {
    // Check RIDDLC_PATH env var first
    Option(System.getenv("RIDDLC_PATH")).flatMap { p =>
      val f = new File(p)
      if (f.exists() && f.canExecute) Some(f) else None
    }.orElse {
      // Search PATH for riddlc
      val pathDirs = sys.env.getOrElse("PATH", "").split(
        File.pathSeparatorChar
      )
      pathDirs.iterator.map { dir =>
        new File(dir, "riddlc")
      }.find { f =>
        f.exists() && f.canExecute
      }
    }
  }

  private[plugin] def findOnPathOrFail(log: Logger): File = {
    findOnPath match {
      case Some(f) => f
      case None =>
        sys.error(
          "riddlc not found. Set riddlcVersion to enable " +
          "auto-download, set riddlcPath to an explicit " +
          "binary, or install riddlc on your PATH."
        )
    }
  }

  // --- Version checking ---

  private[plugin] def versionTriple(
    version: String
  ): (Int, Int, Int) = {
    val trimmed = version.indexOf('-') match {
      case x: Int if x < 0 => version
      case y: Int           => version.take(y)
    }
    val parts = trimmed.split('.')
    if (parts.length < 3) {
      throw new IllegalArgumentException(
        s"riddlc version ($version) has insufficient " +
        "semantic versioning parts."
      )
    } else {
      (parts(0).toInt, parts(1).toInt, parts(2).toInt)
    }
  }

  private[plugin] def versionSameOrLater(
    actualVersion: String,
    minVersion: String
  ): Boolean = {
    if (actualVersion != minVersion) {
      val (aJ, aN, aP) = versionTriple(actualVersion)
      val (mJ, mN, mP) = versionTriple(minVersion)
      aJ > mJ ||
        ((aJ == mJ) &&
         ((aN > mN) || ((aN == mN) && (aP >= mP))))
    } else { true }
  }

  // Pattern to strip ANSI escape sequences from output
  private[plugin] val AnsiPattern: Regex =
    "\u001b\\[[0-9;]*m".r

  private[plugin] def checkVersion(
    binary: File,
    minimumVersion: String
  ): Unit = {
    val check = Seq(
      binary.getAbsolutePath,
      "--no-ansi-messages", "version"
    )
    val raw = check.!!<.trim
    // Strip any residual ANSI codes and [info] prefix
    val actualVersion = AnsiPattern.replaceAllIn(raw, "")
      .replaceAll("(?i)\\[info]\\s*", "").trim
    val minVersion = minimumVersion.trim
    if (!versionSameOrLater(actualVersion, minVersion)) {
      throw new IllegalArgumentException(
        s"riddlc version $actualVersion is below minimum " +
        s"required: $minVersion"
      )
    }
  }

  // --- Conf file discovery ---

  private[plugin] def findConfFiles(
    srcDir: File,
    exclusions: Seq[String]
  ): Seq[File] = {
    if (!srcDir.exists()) {
      Seq.empty
    } else {
      var finder: PathFinder = srcDir ** "*.conf"
      for (ex <- exclusions) {
        finder = finder --- (srcDir / ex ** "*.conf")
      }
      // Also exclude standard build directories
      finder = finder --- (srcDir / "target" ** "*.conf")
      finder = finder --- (srcDir / "project" ** "*.conf")
      finder.get.sorted
    }
  }

  // --- Process execution ---

  private[plugin] def runRiddlcProcess(
    binary: File,
    globalOptions: Seq[String],
    args: Seq[String],
    workDir: File,
    log: Logger
  ): Int = {
    log.info(s"Running: riddlc ${args.mkString(" ")}")
    val errOutput = new StringBuilder
    val outOutput = new StringBuilder
    val proc = Process(
      binary.getAbsolutePath +: (globalOptions ++ args),
      workDir
    ).run(new ProcessIO(
      _.close(),
      BasicIO.processFully { out =>
        outOutput.append(out).append("\n"); ()
      },
      BasicIO.processFully { err =>
        errOutput.append(err).append("\n"); ()
      }
    ))
    val exitCode = proc.exitValue()

    // Log output
    val out = outOutput.toString.trim
    if (out.nonEmpty) log.info(out)
    if (exitCode != 0) {
      val err = errOutput.toString.trim
      if (err.nonEmpty) log.error(err)
    }
    exitCode
  }

  // --- Batch operations ---

  /** Run an operation on each .conf file, collecting
    * failures. Fails the build if any model fails.
    */
  private[plugin] def batchConfOperation(
    binary: File,
    srcDir: File,
    confFiles: Seq[File],
    globalOptions: Seq[String],
    operationName: String,
    log: Logger
  )(
    argsBuilder: File => Seq[String]
  ): Unit = {
    log.info(
      s"Running $operationName on ${confFiles.size} " +
      s"model(s)..."
    )

    var failures = List.empty[(String, String)]
    var successes = 0

    confFiles.foreach { conf =>
      val relPath = srcDir.toPath
        .relativize(conf.toPath).toString
      val args = argsBuilder(conf)
      val errBuf = new StringBuilder
      val outBuf = new StringBuilder
      val proc = Process(
        binary.getAbsolutePath +: (globalOptions ++ args),
        conf.getParentFile
      ).run(new ProcessIO(
        _.close(),
        BasicIO.processFully { out =>
          outBuf.append(out).append("\n"); ()
        },
        BasicIO.processFully { err =>
          errBuf.append(err).append("\n"); ()
        }
      ))
      val exitCode = proc.exitValue()

      if (exitCode != 0) {
        val detail =
          if (errBuf.nonEmpty) errBuf.toString.trim
          else outBuf.toString.trim
        failures = (relPath, detail) :: failures
        log.error(s"  FAILED: $relPath")
      } else {
        successes += 1
        log.info(s"  OK: $relPath")
      }
    }

    if (failures.nonEmpty) {
      log.error("")
      log.error(
        s"${failures.size} model(s) failed $operationName:"
      )
      failures.reverse.foreach { case (path, detail) =>
        log.error(s"--- $path ---")
        if (detail.nonEmpty) log.error(detail)
      }
      sys.error(
        s"${failures.size} of ${confFiles.size} models " +
        s"failed riddlc $operationName"
      )
    } else {
      log.info(
        s"All ${confFiles.size} models passed " +
        s"$operationName ($successes succeeded)."
      )
    }
  }

  /** Resolve conf files: multi-conf from sourceDir, or
    * single-conf fallback. Returns empty if nothing found.
    */
  private[plugin] def resolveConfs(
    srcDir: File,
    exclusions: Seq[String],
    singleConf: File,
    log: Logger
  ): Seq[File] = {
    val multiConfs = findConfFiles(srcDir, exclusions)
    if (multiConfs.nonEmpty) {
      multiConfs
    } else if (singleConf.exists()) {
      Seq(singleConf)
    } else {
      log.warn(
        "No .conf files found in " +
        s"${srcDir.getAbsolutePath} and riddlcConf " +
        s"${singleConf.getAbsolutePath} does not exist"
      )
      Seq.empty
    }
  }

  // --- Interactive command support ---

  private[plugin] def runRiddlcAction(
    state: State,
    args: Seq[String]
  ): State = {
    val project = Project.extract(state)
    val (s1, binary) = project.runTask(riddlcBinary, state)
    val options: Seq[String] = project.get(riddlcOptions)
    val log = project.get(sLog)
    val exitCode = runRiddlcProcess(
      binary, options, args,
      project.get(baseDirectory), log
    )
    if (exitCode != 0) {
      log.error(s"riddlc exited with code $exitCode")
    }
    s1
  }

  // --- Project settings ---

  private val userHome: File =
    file(System.getProperty("user.home"))

  override def projectSettings: Seq[Setting[_]] = Seq(
    // Setting defaults
    riddlcPath := None,
    riddlcVersion := SbtRiddlPluginBuildInfo.version,
    riddlcCacheDir := userHome / ".cache" / "riddlc",
    riddlcMinVersion := SbtRiddlPluginBuildInfo.version,
    riddlcOptions := Seq("--show-times"),
    riddlcConf := baseDirectory.value / "src" / "main" /
      "riddl" / "riddlc.conf",
    riddlcSourceDir := baseDirectory.value / "src" /
      "main" / "riddl",
    riddlcConfExclusions := Seq("patterns"),
    riddlcValidateOnCompile := false,

    // --- Task implementations ---

    riddlcDownload := {
      val log = streams.value.log
      val ver = riddlcVersion.value
      val cache = riddlcCacheDir.value
      downloadRiddlc(cache, ver, log)
    },

    riddlcBinary := {
      val log = streams.value.log
      val binary = riddlcPath.value match {
        case Some(path) =>
          if (!path.exists() || !path.canExecute) {
            sys.error(
              s"riddlcPath $path does not exist or " +
              "is not executable"
            )
          }
          path
        case None =>
          val ver = riddlcVersion.value
          if (ver.nonEmpty) {
            downloadRiddlc(riddlcCacheDir.value, ver, log)
          } else {
            findOnPathOrFail(log)
          }
      }
      checkVersion(binary, riddlcMinVersion.value)
      binary
    },

    riddlcInfo := {
      val binary = riddlcBinary.value
      val log = streams.value.log
      runRiddlcProcess(
        binary, riddlcOptions.value, Seq("info"),
        baseDirectory.value, log
      )
      ()
    },

    riddlcShowVersion := {
      val binary = riddlcBinary.value
      val log = streams.value.log
      runRiddlcProcess(
        binary, Seq.empty, Seq("version"),
        baseDirectory.value, log
      )
      ()
    },

    riddlcValidate := {
      val binary = riddlcBinary.value
      val srcDir = riddlcSourceDir.value
      val exclusions = riddlcConfExclusions.value
      val options = riddlcOptions.value
      val singleConf = riddlcConf.value
      val log = streams.value.log

      val confs = resolveConfs(
        srcDir, exclusions, singleConf, log
      )
      if (confs.nonEmpty) {
        batchConfOperation(
          binary, srcDir, confs, options, "validate", log
        ) { conf =>
          extractInputFile(conf) match {
            case Some(inputFile) =>
              val riddlFile =
                conf.getParentFile / inputFile
              Seq("validate", riddlFile.getAbsolutePath)
            case None =>
              sys.error(
                "Could not extract input-file from " +
                conf.getAbsolutePath
              )
          }
        }
      }
    },

    riddlcParse := {
      val binary = riddlcBinary.value
      val srcDir = riddlcSourceDir.value
      val exclusions = riddlcConfExclusions.value
      val options = riddlcOptions.value
      val singleConf = riddlcConf.value
      val log = streams.value.log

      val confs = resolveConfs(
        srcDir, exclusions, singleConf, log
      )
      if (confs.nonEmpty) {
        batchConfOperation(
          binary, srcDir, confs, options, "parse", log
        ) { conf =>
          extractInputFile(conf) match {
            case Some(inputFile) =>
              val riddlFile =
                conf.getParentFile / inputFile
              Seq("parse", riddlFile.getAbsolutePath)
            case None =>
              sys.error(
                "Could not extract input-file from " +
                conf.getAbsolutePath
              )
          }
        }
      }
    },

    riddlcBastify := {
      val binary = riddlcBinary.value
      val srcDir = riddlcSourceDir.value
      val exclusions = riddlcConfExclusions.value
      val options = riddlcOptions.value
      val singleConf = riddlcConf.value
      val log = streams.value.log

      val confs = resolveConfs(
        srcDir, exclusions, singleConf, log
      )
      if (confs.nonEmpty) {
        batchConfOperation(
          binary, srcDir, confs, options, "bastify", log
        ) { conf =>
          extractInputFile(conf) match {
            case Some(inputFile) =>
              val riddlFile =
                conf.getParentFile / inputFile
              Seq("bastify", riddlFile.getAbsolutePath)
            case None =>
              sys.error(
                "Could not extract input-file from " +
                conf.getAbsolutePath
              )
          }
        }
      }
    },

    riddlcPrettify := {
      val binary = riddlcBinary.value
      val srcDir = riddlcSourceDir.value
      val exclusions = riddlcConfExclusions.value
      val options = riddlcOptions.value
      val singleConf = riddlcConf.value
      val log = streams.value.log

      val confs = resolveConfs(
        srcDir, exclusions, singleConf, log
      )
      if (confs.nonEmpty) {
        batchConfOperation(
          binary, srcDir, confs, options, "prettify", log
        ) { conf =>
          extractInputFile(conf) match {
            case Some(inputFile) =>
              val modelDir = conf.getParentFile
              val riddlFile = modelDir / inputFile
              Seq(
                "prettify",
                riddlFile.getAbsolutePath,
                "-o", modelDir.getAbsolutePath
              )
            case None =>
              sys.error(
                "Could not extract input-file from " +
                conf.getAbsolutePath
              )
          }
        }
      }
    },

    // Command wrappers for interactive sbt use
    commands ++= Seq(
      Command.command("validate") { s =>
        "riddlcValidate" :: s
      },
      Command.command("bastify") { s =>
        "riddlcBastify" :: s
      },
      Command.command("prettify") { s =>
        "riddlcPrettify" :: s
      },
      Command.command("parse") { s =>
        "riddlcParse" :: s
      },
      Command.command("info") { s =>
        "riddlcInfo" :: s
      },
      Command.args("riddlc", "<args>") {
        (state, args) => runRiddlcAction(state, args)
      }
    )
  )
}
