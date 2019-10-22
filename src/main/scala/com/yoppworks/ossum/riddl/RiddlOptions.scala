package com.yoppworks.ossum.riddl

import java.io.File

import scopt.OParserBuilder

/** Command Line Options for Riddl compiler program */
case class RiddlOptions(
  dryRun: Boolean = false,
  verbose: Boolean = false,
  quiet: Boolean = false,
  suppressWarnings: Boolean = false,
  suppressMissingWarnings: Boolean = false,
  suppressStyleWarnings: Boolean = false,
  input: Seq[File] = Seq.empty[File],
  outputDir: Option[File] = None,
  configurationFile: Option[File] = None
)

object RiddlOptions {
  import scopt.OParser

  val builder: OParserBuilder[RiddlOptions] =
    scopt.OParser.builder[RiddlOptions]

  val parser: OParser[Unit, RiddlOptions] = {
    import builder._
    OParser.sequence(
      programName("riddlc"),
      head("riddlc", BuildInfo.version),
      opt[Boolean]('d', "dry-run").action((x, c) => c.copy(dryRun = x)),
      opt[Boolean]('v', "verbose").action((x, c) => c.copy(verbose = x)),
      opt[Boolean]('q', "quiet").action((x, c) => c.copy(quiet = x)),
      opt[Boolean]('w', name = "supress-warnings")
        .action((x, c) => c.copy(suppressWarnings = x)),
      opt[Boolean]('m', name = "supress-missing-warnings")
        .action((x, c) => c.copy(suppressWarnings = x)),
      opt[Boolean]('s', name = "supress-style-warnings")
        .action((x, c) => c.copy(suppressWarnings = x)),
      arg[Seq[File]]("<input file>...>")
        .optional()
        .action((x, c) => c.copy(input = c.input ++ x))
        .text("optional unbounded list of files to compile"),
      arg[File]("<output dir>")
        .optional()
        .action((x, c) => c.copy(outputDir = Some(x)))
        .text("optional unbounded list of files to compile"),
      arg[File]("<configuration file>")
        .optional()
        .action((x, c) => c.copy(configurationFile = Some(x)))
        .text("code generation configuration file")
    )
  }

}
