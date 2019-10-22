package com.yoppworks.ossum.riddl

import scopt.OParser

/** RIDDL Main Program
  *
  */
object RIDDL {
  final def main(args: Array[String]): Unit = {
    try {
      // OParser.parse returns Option[Config]
      OParser.parse(RiddlOptions.parser, args, RiddlOptions()) match {
        case Some(options) =>
          // do something
          if (!options.quiet)
            println("Reactive Interface to Domain Definition Language")
        case _ =>
          // arguments are bad, error message will have been displayed
          System.exit(1)
      }
    } catch {
      case (xcptn: Exception) =>
        println(xcptn.getClass.getName + ": " + xcptn.getMessage)
        System.exit(2)
    }
  }
}
