package com.yoppworks.ossum.riddl.generation.hugo

import scala.reflect.ClassTag
import scala.util.Try
import scala.util.matching.Regex

object Templates {

  final def forHugo(repr: HugoNode): Either[Throwable, TemplateFile] = Loader.getTemplate(repr)

  private sealed trait TemplateSlot
  private case class NotLoaded(resource: String) extends TemplateSlot
  private case class LoadedTemplate(template: TemplateFile) extends TemplateSlot
  private case class FailedToLoad(error: Throwable) extends TemplateSlot

  private object Loader {
    private[this] final val lockBox = new AnyRef
    private lazy val classLoader = Thread.currentThread.getContextClassLoader

    private[this] val templatesMap: collection.mutable.Map[String, TemplateSlot] = collection
      .mutable.Map(ResourceNames.all: _*).map { case (key, value) => (key, NotLoaded(value)) }

    final def getTemplate(forRepr: HugoNode): Either[Throwable, TemplateFile] = {
      val name = forRepr match {
        case _: HugoType => "HugoType"
        case _           => forRepr.getClass.getSimpleName.stripSuffix("$")
      }
      lockBox.synchronized {
        templatesMap.get(name) match {
          case Some(NotLoaded(resource)) =>
            val (slot, result) = loadOrFail(name, resource)
            templatesMap.update(name, slot)
            result

          case Some(LoadedTemplate(template)) => Right(template)
          case Some(FailedToLoad(error))      => Left(error)
          case None => Left(new NoSuchElementException(s"No template for $name"))
        }
      }
    }

    private def loadOrFail(
      named: String,
      resource: String
    ): (TemplateSlot, Either[Throwable, TemplateFile]) = {
      val loaded = tryLoadTemplate(named, resource)
      val slot = loaded.fold(FailedToLoad, LoadedTemplate)
      (slot, loaded.toEither)
    }

    private def tryLoadTemplate(named: String, resource: String): Try[TemplateFile] =
      Try(classLoader.getResourceAsStream(resource)).flatMap { inputStream =>
        try TemplateFile(inputStream, named)
        finally inputStream.close()
      }
  }

  private object ResourceNames {
    @inline
    private final def mkTemplateResource[T <: HugoNode: ClassTag](name: String): (String, String) =
      (implicitly[ClassTag[T]].runtimeClass.getSimpleName, s"components/$name")

    val context = mkTemplateResource[HugoContext]("context.md")
    val domain = mkTemplateResource[HugoDomain]("domain.md")
    val entity = mkTemplateResource[HugoEntity]("entity.md")
    val types = mkTemplateResource[HugoType]("types.md")

    val all = Seq(context, domain, entity, types)
  }
}

final class TemplateFile private (
  templateName: String,
  lines: Seq[String]
)(private val replacements: Map[String, Regex]) {
  override def toString: String = lines.mkString("\n")
  def replacementTokens: Set[String] = replacements.keySet

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
    .map(name => (name, new Regex(s"\\{\\{$name}}"))).toMap

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
