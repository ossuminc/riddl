/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.find

import com.ossuminc.riddl.commands.project.ProjectedNode
import com.ossuminc.riddl.language.At

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable

/** Running external commands, and rewriting spans.
  *
  * `java.lang.ProcessBuilder` rather than `scala.sys.process`: this module builds for Native as well
  * as the JVM, and the java API is available on both.
  */
object FindEditor {

  final case class ProcResult(exit: Int, stdout: String)

  /** Runs `cmd`, feeding `stdin` to it. `inherit` sends the child's stdout to ours, which is what
    * `-exec` does in find; `-replace` captures it instead, because the output IS the replacement.
    */
  def run(cmd: Seq[String], stdin: String, inherit: Boolean): ProcResult = {
    val pb = new java.lang.ProcessBuilder(cmd*)
    pb.redirectError(java.lang.ProcessBuilder.Redirect.INHERIT)
    if inherit then pb.redirectOutput(java.lang.ProcessBuilder.Redirect.INHERIT)
    val p = pb.start()
    val os = p.getOutputStream
    try
      os.write(stdin.getBytes("UTF-8"))
      os.flush()
    catch case _: Exception => () // the child may not read stdin; a broken pipe is not our problem
    finally try os.close() catch case _: Exception => ()
    val out = if inherit then "" else new String(p.getInputStream.readAllBytes(), "UTF-8")
    ProcResult(p.waitFor(), out)
  }

  /** One pending rewrite of a source span. */
  final case class Edit(file: Path, start: Int, end: Int, replacement: String, what: String)

  /** Plans the edits, refusing anything that cannot be applied unambiguously.
    *
    * **Overlapping spans are rejected outright, and that subsumes the innermost-first rule.** Two
    * matches overlap exactly when one contains the other or they interleave; either way the text of
    * one edit would be rewritten by the other, and which won would depend on application order. A
    * `find` that silently dropped one of two requested edits would be the confident-answer-over-
    * nothing failure this command exists to end, so it refuses the whole run instead.
    *
    * With overlaps rejected, applying edits back-to-front within each file is sufficient: every
    * edit's offsets remain valid because nothing earlier in the file has moved yet.
    */
  def plan(edits: Seq[Edit]): Either[Seq[String], Map[Path, Seq[Edit]]] = {
    val byFile = edits.groupBy(_.file)
    val problems = mutable.ListBuffer.empty[String]
    byFile.foreach { case (file, es) =>
      val sorted = es.sortBy(_.start)
      sorted.sliding(2).foreach {
        case Seq(a, b) if b.start < a.end =>
          problems.append(
            s"$file: '${a.what}' [${a.start},${a.end}) overlaps '${b.what}' [${b.start},${b.end})"
          )
        case _ => ()
      }
    }
    if problems.nonEmpty then Left(problems.toSeq)
    else Right(byFile.view.mapValues(_.sortBy(-_.start)).toMap)
  }

  /** Applies edits to text, back to front. */
  def apply(original: String, edits: Seq[Edit]): String = {
    val sb = new StringBuilder(original)
    edits.foreach(e => sb.replace(e.start, e.end, e.replacement))
    sb.toString
  }

  /** The exact source text of a node's span.
    *
    * Handed to every `-exec`/`-replace` script as the record's `source` key, and it is what makes
    * `-replace` usable at all: a script that cannot see the text it is replacing has to synthesize
    * the replacement from the structured record alone, so even leaving a node ALONE would be a
    * reconstruction. With it, the identity transform is `jq -r .source` and an edit is that text
    * piped through whatever the author likes.
    */
  def spanText(n: ProjectedNode): Option[String] = {
    val loc = n.value.loc
    val data = loc.source.data
    if loc.offset < 0 || loc.endOffset > data.length || loc.endOffset < loc.offset then None
    else Some(data.substring(loc.offset, loc.endOffset))
  }

  /** The record a script receives on stdin: the projection's own record plus `source`. */
  def recordFor(n: ProjectedNode): ujson.Obj = {
    val obj = ujson.Obj.from(n.record.value.toSeq)
    spanText(n).foreach(t => obj("source") = ujson.Str(t))
    obj
  }

  /** The file a node lives in, when it is a real file on disk.
    *
    * The node's URL is asked FIRST, and `origin` is only a fallback. `origin` is the SHORT name
    * error messages use -- typically the bare filename -- so resolving it as a path silently
    * depends on the process's working directory: editing a model worked when riddlc was run from
    * the model's own directory and reported "has no source file to edit" from anywhere else.
    * `toFullPathString` is absolute and independent of where riddlc was started.
    *
    * A `None` here is not always a defect: a model parsed from a string or fetched over http has
    * no file to rewrite, and refusing to edit it is correct.
    */
  def fileOf(n: ProjectedNode): Option[Path] = {
    val source = n.value.loc.source
    val candidates =
      (if source.root.isEmpty then Nil else Seq(source.root.toFullPathString)) ++
        (if source.origin.isEmpty || source.origin == "empty" then Nil else Seq(source.origin))
    candidates.iterator
      .map(Paths.get(_))
      .find(p => Files.exists(p) && Files.isRegularFile(p))
  }
}
