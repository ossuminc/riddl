/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.hugo

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language.Folding.PathResolutionState
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.language._

import java.nio.file.Path

/**
 * The processing state for the Hugo Translator
 * @param root RootContainer that was parsed
 * @param symbolTable A symbolTable for the names of things
 * @param options The options specific to Hugo Translator
 * @param commonOptions The common options all commands use
 */
case class HugoTranslatorState(
  root: AST.Definition,
  symbolTable: SymbolTable,
  options: HugoCommand.Options = HugoCommand.Options(),
  commonOptions: CommonOptions = CommonOptions())
    extends TranslatorState[MarkdownWriter]
    with PathResolutionState[HugoTranslatorState] {

  def addFile(parents: Seq[String], fileName: String): MarkdownWriter = {
    val parDir = parents.foldLeft(options.contentRoot) { (next, par) =>
      next.resolve(par)
    }
    val path = parDir.resolve(fileName)
    val mdw = MarkdownWriter(path, this)
    addFile(mdw)
    mdw
  }

  var terms = Seq.empty[GlossaryEntry]

  def addToGlossary(
    d: Definition,
    stack: Seq[Definition]
  ): HugoTranslatorState = {
    if (options.withGlossary) {
      val parents = makeParents(stack)
      val entry = GlossaryEntry(
        d.id.value,
        d.kind,
        d.brief.map(_.s).getOrElse("-- undefined --"),
        parents :+ d.id.value,
        makeDocLink(d, parents),
        makeSourceLink(d)
      )
      terms = terms :+ entry
    }
    this
  }

  def makeParents(stack: Seq[Definition]): Seq[String] = {
    // The stack goes from most nested to highest. We don't want to change the
    // stack (its mutable) so we copy it to a Seq first, then reverse it, then
    // drop all the root containers (file includes) to finally end up at a domin
    // and then map to just the name of that domain.
    stack.reverse.dropWhile(_.isRootContainer).map(_.id.format)
  }

  def pathRelativeToRepo(path: Path): Option[String] = {
    options.inputFile match {
      case Some(inFile) =>
        val pathAsString = path.toAbsolutePath.toString
        val inDirAsString = inFile.getParent.toAbsolutePath.toString
        if (pathAsString.startsWith(inDirAsString)) {
          val result = pathAsString.drop(inDirAsString.length + 1)
          Some(result)
        } else { Option.empty[String] }
      case None => Option.empty[String]
    }
  }

  /** Generate a string that is the file path portion of a url including the
    * line number.
    */
  def makeFilePath(definition: Definition): Option[String] = {
    definition.loc.source match {
      case FileParserInput(file) =>
        pathRelativeToRepo(file.getAbsoluteFile.toPath)
      case _ => Option.empty[String]
    }
  }

  /** Generate a string that contains the name of a definition that is markdown
    * linked to the definition in its source. For example, given sourceURL
    * option of https://github.com/a/b and for an editPath option of
    * src/main/riddl and for a Location that has Org/org.riddl at line 30, we
    * would generate this URL:
    * `https://github.com/a/b/blob/main/src/main/riddl/Org/org.riddl#L30` Note
    * that that this works through recursive path identifiers to find the first
    * type that is not a reference Note: this only works for github sources
    * @param definition
    *   The definition for which we want the link
    * @return
    *   a string that gives the source link for the definition
    */
  def makeSourceLink(
    definition: Definition
  ): String = {
    options.sourceURL match {
      case Some(url) => options.viewPath match {
          case Some(viewPath) => makeFilePath(definition) match {
              case Some(filePath) => Path.of(url.toString, viewPath, filePath)
                  .toString
              case _ => ""
            }
          case None => ""
        }
      case None => ""
    }
  }

  def makeDocLink(definition: Definition, parents: Seq[String]): String = {
    val pars = ("/" + parents.mkString("/")).toLowerCase
    val result = definition match {
      case _: OnClause => pars + "#" + definition.id.value.toLowerCase
      case _: Field | _: Enumerator | _: Invariant | _: Inlet | _: Outlet |
          _: InletJoint | _: OutletJoint | _: AuthorInfo | _: SagaStep |
          _: Include | _: RootContainer | _: Term => pars
      case _ => pars + "/" + definition.id.value.toLowerCase
    }
    // deal with Geekdoc's url processor
    result.replace(" ", "-")
  }

  def makeIndex(root: RootContainer): Unit = {
    val mdw = addFile(Seq.empty[String], "_index.md")
    mdw.fileHead("Index", 10, Option("The main index to the content"))
    mdw.h2("Domains")
    val domains = root.contents.sortBy(_.id.value)
      .map(d => s"[${d.id.value}](${d.id.value.toLowerCase}/)")
    mdw.list(domains)
    mdw.h2("Indices")
    val glossary =
      if (options.withGlossary) { Seq("[Glossary](glossary)") }
      else { Seq.empty[String] }
    val todoList =
      if (options.withTODOList) { Seq("[To Do List](todolist)") }
      else { Seq.empty[String] }
    mdw.list(glossary ++ todoList)
    mdw.emitIndex("Full")
  }

  val lastFileWeight = 999

  def makeGlossary(): Unit = {
    if (options.withGlossary) {
      val mdw = addFile(Seq.empty[String], "glossary.md")
      mdw.emitGlossary(lastFileWeight, terms)
    }
  }

  def findAuthor(
    defn: Definition,
    parents: Seq[Definition]
  ): Seq[AuthorInfo] = {
    val result = AST.authorsOf(defn) match {
      case s if s.isEmpty =>
        parents.find(x => x.hasAuthors) match {
          case None    => Seq.empty[AuthorInfo]
          case Some(d) => AST.authorsOf(d)
        }
      case s => s
    }
    result
  }

  def makeToDoList(root: RootContainer): Unit = {
    if (options.withTODOList) {
      val finder = Finder(root)
      val items: Seq[(String, String, String, String)] = for {
        (defn, pars) <- finder.findEmpty
        item = defn.identify
        authors = findAuthor(defn, pars)
        author =
          if (authors.isEmpty) { "Unspecified Author" }
          else {
            authors.map(x => s"${x.name.s} &lt;${x.email.s}&gt;").mkString(", ")
          }
        parents = makeParents(pars)
        path = parents.mkString(".")
        link = makeDocLink(defn, parents)
      } yield { (item, author, path, link) }

      val map = items.groupBy(_._2).view
        .mapValues(_.map { case (item, _, path, link) =>
          s"[$item In $path]($link)"
        }).toMap
      val mdw = addFile(Seq.empty[String], "todolist.md")
      mdw.fileHead(
        "To Do List",
        lastFileWeight - 1,
        Option("A list of definitions needing more work")
      )
      mdw.h2("Definitions With Missing Content")
      for { (key, items) <- map } {
        mdw.h3(key)
        mdw.list(items)
      }
    }
  }

  def close(root: RootContainer): Seq[Path] = {
    makeIndex(root)
    makeGlossary()
    makeToDoList(root)
    files.foreach(_.write())
    files.map(_.filePath).toSeq
  }
}
