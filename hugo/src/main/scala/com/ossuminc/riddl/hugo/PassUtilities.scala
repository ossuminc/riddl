package com.ossuminc.riddl.hugo
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.FileParserInput
import com.ossuminc.riddl.passes.{PassInput, PassesOutput, PassesResult}

import java.nio.file.Path
import scala.collection.mutable

trait PassUtilities {
  def outputs: PassesOutput
  def options: HugoCommand.Options
  val inputFile: Option[Path] = options.inputFile
  protected val messages: Messages.Accumulator

  lazy val newline: String = System.getProperty("line.separator")

  def makeFullName(definition: Definition): String = {
    val defs = outputs.symbols.parentsOf(definition).reverse :+ definition
    defs.map(_.id.format).mkString(".")
  }

  def makeParents(stack: Seq[Definition]): Seq[Definition] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    stack.toSeq.reverse.dropWhile(_.isRootContainer)
  }

  def makeStringParents(stack: Seq[Definition]): Seq[String] = {
    makeParents(stack).map(_.id.format)
  }

  def makeBreadCrumbs(parents: Seq[Definition]): String = {
    parents
      .map { defn =>
        val link = makeDocLink(defn, parents.map(_.id.value))
        s"[${defn.id.value}]($link)"
      }
      .mkString("/")
  }

  def makeDocLink(definition: NamedValue): String = {
    val parents = makeStringParents(outputs.symbols.parentsOf(definition))
    makeDocLink(definition, parents)
  }

  def makeDocLink(definition: NamedValue, parents: Seq[String]): String = {
    val pars = ("/" + parents.mkString("/")).toLowerCase
    val result = definition match {
      case _: OnMessageClause | _: OnInitClause | _: OnTerminationClause | _: OnOtherClause | _: Inlet | _: Outlet =>
        pars + "#" + definition.id.value.toLowerCase
      case _: Field | _: Enumerator | _: Invariant | _: Author | _: SagaStep | _: Include[Definition] @unchecked |
          _: Root | _: Term =>
        pars
      case _ =>
        if parents.isEmpty then pars + definition.id.value.toLowerCase
        else pars + "/" + definition.id.value.toLowerCase
    }
    // deal with Geekdoc's url processor
    result.replace(" ", "-")
  }

  def makeDocAndParentsLinks(definition: NamedValue): String = {
    val parents = outputs.symbols.parentsOf(definition)
    val docLink = makeDocLink(definition, makeStringParents(parents))
    if parents.isEmpty then { s"[${definition.identify}]($docLink)" }
    else {
      parents.headOption match
        case None =>
          messages.addError(definition.loc, s"No parents found for definition '${definition.identify}")
          ""
        case Some(parent: Definition) =>
          val parentLink = makeDocLink(parent, makeStringParents(parents.drop(1)))
          s"[${definition.identify}]($docLink) in [${parent.identify}]($parentLink)"
    }
  }

  /** Generate a string that is the file path portion of a url including the line number.
    */
  def makeFilePath(definition: Definition): Option[String] = {
    definition.loc.source match {
      case FileParserInput(file) =>
        pathRelativeToRepo(file.getAbsoluteFile.toPath)
      case _ => Option.empty[String]
    }
  }

  def pathRelativeToRepo(path: Path): Option[String] = {
    options.inputFile match {
      case Some(inFile) =>
        val pathAsString = path.toAbsolutePath.toString
        options.withInputFile { inputFile =>
          Right(inputFile.getParent.toAbsolutePath.toString)
        } match {
          case Right(inDirAsString) =>
            if pathAsString.startsWith(inDirAsString) then {
              val result = pathAsString.drop(inDirAsString.length + 1)
              Some(result)
            } else {
              Option.empty[String]
            }
          case Left(messages) =>
            messages.appendedAll(messages)
            Option.empty[String]
        }
      case None => Option.empty[String]
    }
  }

  /** Generate a string that contains the name of a definition that is markdown linked to the definition in its source.
    * For example, given sourceURL option of https://github.com/a/b and for an editPath option of src/main/riddl and for
    * a Location that has Org/org.riddl at line 30, we would generate this URL:
    * `https://github.com/a/b/blob/main/src/main/riddl/Org/org.riddl#L30` Note that that this works through recursive
    * path identifiers to find the first type that is not a reference Note: this only works for github sources
    * @param definition
    *   The definition for which we want the link
    * @return
    *   a string that gives the source link for the definition
    */
  def makeSourceLink(
    definition: Definition
  ): String = {
    options.sourceURL match {
      case Some(url) =>
        options.viewPath match {
          case Some(viewPath) =>
            makeFilePath(definition) match {
              case Some(filePath) =>
                val lineNo = definition.loc.line
                url.toExternalForm ++ "/" ++ Path.of(viewPath, filePath).toString ++ s"#L$lineNo"
              case _ => ""
            }
          case None => ""
        }
      case None => ""
    }
  }
}
