package com.yoppworks.ossum.riddl.generation.hugo

import cats.instances.list._
import cats.instances.try_._
import cats.syntax.all._
import com.yoppworks.ossum.riddl.language.AST
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class GeneratorOptions(
  outputPath: String,
  projectName: String = "Project",
  verbose: Boolean = false)

object HugoGenerator {
  private val templateResources = "template\\/"
  private def nonIO(str: => String): Try[Unit] = Success(())
  private def infoIO(str: => String): Try[Unit] = Try(println(str))

  def apply(root: AST.RootContainer, options: GeneratorOptions): Unit =
    generate(root, options) match {
      case Success(_)   => ()
      case Failure(err) => printException(err)
    }

  def attempt(root: AST.RootContainer, options: GeneratorOptions): Either[Throwable, Unit] =
    generate(root, options).toEither

  private def printException(err: Throwable): Unit = {
    println(s"Hugo generation failed due to an error:\n${err.toString}")
    println("Stack Trace: ")
    err.printStackTrace(System.console.writer)
  }

  private def makeDebugIO(verbose: Boolean)(str: => String): Try[Unit] = {
    val logger = if (!verbose) nonIO(_) else infoIO(_)
    logger(str)
  }

  private def generate(root: AST.RootContainer, options: GeneratorOptions): Try[Unit] = for {
    debugIO <- Try(makeDebugIO(options.verbose)(_))

    _ <- infoIO("Processing Riddl AST...")
    unresolved <- Try(LukeAstWalker(root))
    _ <- debugIO("Resolving Riddl references...")
    refs = TypeResolver.unresolved(unresolved).size
    namespace <- Try(TypeResolution(unresolved))
    resolvedRefs = refs - TypeResolver.unresolved(namespace).size
    _ <- infoIO(s"Resolved $resolvedRefs (of $refs) references.")
    _ <- infoIO("Riddl AST processing completed.")

    _ <- infoIO("Generating Hugo documentation...")
    _ <- debugIO("Reading Hugo documentation template...")
    resources <- Resources.listResourcesInJar(templateResources)
    _ <- debugIO(s"Writing Hugo documentation template to ${options.outputPath}")
    _ <- Resources.copyResourcesTo(options.outputPath, resources)
    _ <- debugIO("Writing Hugo documentation content...")
    content = rendering.HugoLayout(namespace, options.projectName.capitalize)
    filesWritten <- rendering.ContentWriter.writeContent(options.outputPath, content)
    _ <- filesWritten.traverse(filename => debugIO(s"Wrote file: $filename"))
    _ <- infoIO(s"Hugo documentation generated in ${options.outputPath}")
  } yield ()

}
