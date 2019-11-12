package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RiddlOptions._
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.Riddl.SysLogger
import com.yoppworks.ossum.riddl.translator.ParadoxTranslator
import com.yoppworks.ossum.riddl.translator.FormatTranslator
import scopt.OParser

/** RIDDL Main Program
  *
  */
object RIDDL {
  final def main(args: Array[String]): Unit = {
    try {
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(options) =>
          options.command match {
            case Parse =>
              parse(options)
            case Validate =>
              validate(options)
            case Translate =>
              translate(options)
            case Unspecified =>
              SysLogger.error(s"A command is required")
          }
        case _ =>
          // arguments are bad, error message will have been displayed
          System.exit(1)
      }
    } catch {
      case xcptn: Throwable =>
        SysLogger.error(xcptn.getClass.getName + ": " + xcptn.getMessage)
    }
  }

  def parse(options: RiddlOptions): Unit = {
    options.inputFile match {
      case Some(file) =>
        Riddl.parse(file.toPath, SysLogger, options) match {
          case None =>
          case Some(_) =>
            SysLogger.info("Completed.")
        }
      case None =>
        SysLogger.error("No input file specified")
    }
  }

  def parseAndValidate(options: RiddlOptions): Option[RootContainer] = {
    options.inputFile match {
      case Some(file) =>
        Riddl.parseAndValidate(file.toPath, SysLogger, options)
      case None =>
        SysLogger.error("No input file specified")
        None
    }
  }

  def validate(options: RiddlOptions): Unit = {
    parseAndValidate(options)
  }

  def translate(options: RiddlOptions): Unit = {
    if (options.configFile.isEmpty) {
      SysLogger.error("No translation configuration file provided")
    } else {
      parseAndValidate(options) match {
        case None =>
        case Some(root) =>
          Riddl.timer(stage = "translate", options.showTimes) {
            options.outputKind match {
              case Kinds.Prettify =>
                val trans = new FormatTranslator
                trans.translate(
                  root,
                  Riddl.SysLogger,
                  options.configFile.map(_.toPath)
                )
              case Kinds.Paradox =>
                val trans = new ParadoxTranslator
                trans.translate(
                  root,
                  Riddl.SysLogger,
                  options.configFile.map(_.toPath)
                )
              case x: Kinds.Value =>
                println(s"Translation $x not yet implemented")
            }
          }
      }
    }
  }
}
