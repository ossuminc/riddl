package com.yoppworks.ossum.riddl.generation.hugo

import scala.reflect.ClassTag
import scala.util.Try
import scala.util.matching.Regex

object Templates {

  private sealed trait TemplateSlot
  private case class NotLoaded(resource: String) extends TemplateSlot
  private case class LoadedTemplate(template: TemplateFile) extends TemplateSlot
  private case class FailedToLoad(error: Throwable) extends TemplateSlot

  private object ResourceNames {
    @inline
    private final def mkTemplateResource[T <: HugoRepr: ClassTag](name: String): (String, String) =
      (s"components/$name", implicitly[ClassTag[T]].runtimeClass.getSimpleName)

    val context = mkTemplateResource[HugoContext]("context.md")
    val domain = mkTemplateResource[HugoDomain]("domain.md")
    val typeAlias = mkTemplateResource[HugoType.Alias]("type-alias.md")
    val typeEnum = mkTemplateResource[HugoType.Enumeration]("type-enum.md")
    val typeRecord = mkTemplateResource[HugoType.Record]("type-record.md")

    val all = Set(context, domain, typeAlias, typeEnum, typeRecord)
  }
}

final class TemplateFile private (
  templateName: String,
  lines: Seq[String]
)(private val replacements: Map[String, Regex]) {

  def replace(name: String, withText: String): TemplateFile = replacements.get(name) match {
    case None => this
    case Some(regex) =>
      val updatedLines = lines.map { line => regex.replaceAllIn(line, withText) }
      new TemplateFile(templateName, updatedLines)(replacements)
  }

  def replaceAll(nameAndReplacement: (String, String)*): TemplateFile =
    if (nameAndReplacement.isEmpty) { this }
    else {
      val toReplace = nameAndReplacement.map { case (name, withText) =>
        (replacements.get(name), withText)
      }.collect { case (Some(regex), withText) => (regex, withText) }
      val updatedLines = lines.map { fileLine =>
        toReplace.foldLeft(fileLine) { case (line, (regex, withText)) =>
          regex.replaceAllIn(line, withText)
        }
      }
      new TemplateFile(templateName, updatedLines)(replacements)
    }

  def writeToFile(file: java.io.File): Try[Unit] = Try {
    import java.io._
    val printer = new PrintWriter(new FileWriter(file))
    try {
      for (line <- lines) { printer.println(line) }
      printer.flush()
    } finally { printer.close() }
  }

}

object TemplateFile {

  def apply(
    fromFile: java.io.File,
    named: Option[String] = None
  ): Try[TemplateFile] = Try {
    val templateName = named.getOrElse(fromFile.getName)
    val fileLines = readAllLinesUnsafe(fromFile)
    val replacements = findReplacements(fileLines)
    new TemplateFile(templateName, fileLines)(replacements)
  }

  def apply(
    inputStream: java.io.InputStream,
    named: String
  ): Try[TemplateFile] = Try {
    val fileLines = readLinesFromStreamUnsafe(inputStream)
    val replacements = findReplacements(fileLines)
    new TemplateFile(named, fileLines)(replacements)
  }

  private val replacementRegex = """\{\{(\w+)}}""".r

  @inline
  private final def findReplacements(lines: Seq[String]): Map[String, Regex] = lines
    .flatMap(replacementRegex.findAllMatchIn(_)).map(_.group(1)).distinct
    .map(name => (name, new Regex("\\{\\{$name}}"))).toMap

  @inline
  private final def readAllLinesUnsafe(file: java.io.File): Seq[String] = {
    val src = scala.io.Source.fromFile(file)
    try src.getLines.toSeq
    finally src.close()
  }

  @inline
  private final def readLinesFromStreamUnsafe(stream: java.io.InputStream): Seq[String] = {
    val src = scala.io.Source.fromInputStream(stream)
    try src.getLines.toSeq
    finally src.close()
  }
}
