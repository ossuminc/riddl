/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At

/** Deep structural comparison utilities for AST nodes
  *
  * This provides recursive comparison of AST structures to verify that
  * serialization/deserialization preserves all structural information.
  */
object DeepASTComparison {

  /** Result of a comparison with detailed information about differences */
  sealed trait ComparisonResult {
    def isSuccess: Boolean
    def path: String
    def message: String

    def format: String = message
  }

  case class Success(path: String) extends ComparisonResult {
    def isSuccess: Boolean = true
    def message: String = s"[ok] $path matches"
  }

  case class Failure(path: String, expected: String, actual: String, detail: String = "")
      extends ComparisonResult {
    def isSuccess: Boolean = false
    def message: String = {
      val base = s"[FAIL] $path: expected $expected but got $actual"
      if detail.nonEmpty then s"$base ($detail)" else base
    }
  }

  /** Compare two AST values deeply
    *
    * @param original
    *   The original AST value
    * @param reconstructed
    *   The reconstructed AST value
    * @param path
    *   The current path in the tree (for error reporting)
    * @return
    *   List of comparison results (failures indicate problems)
    */
  def compare(
    original: RiddlValue,
    reconstructed: RiddlValue,
    path: String = "root"
  ): List[ComparisonResult] = {

    // First check: same type?
    if original.getClass != reconstructed.getClass then
      return List(
        Failure(
          path,
          original.getClass.getSimpleName,
          reconstructed.getClass.getSimpleName,
          "Type mismatch"
        )
      )
    end if

    // Compare based on node type
    (original, reconstructed) match {
      // Vital Definitions
      case (d1: Domain, d2: Domain)   => compareDomain(d1, d2, path)
      case (c1: Context, c2: Context) => compareContext(c1, c2, path)
      case (e1: Entity, e2: Entity)   => compareEntity(e1, e2, path)
      case (m1: Module, m2: Module)   => compareModule(m1, m2, path)

      // Type Definitions
      case (t1: Type, t2: Type) => compareType(t1, t2, path)

      // Other definitions - add more as needed
      case (a1: Adaptor, a2: Adaptor)       => compareAdaptor(a1, a2, path)
      case (s1: Streamlet, s2: Streamlet)   => compareStreamlet(s1, s2, path)
      case (p1: Projector, p2: Projector)   => compareProjector(p1, p2, path)
      case (r1: Repository, r2: Repository) => compareRepository(r1, r2, path)
      case (h1: Handler, h2: Handler)       => compareHandler(h1, h2, path)
      case (f1: Function, f2: Function)     => compareFunction(f1, f2, path)

      // Comments and metadata
      case (c1: Comment, c2: Comment)         => compareComment(c1, c2, path)
      case (d1: Description, d2: Description) => compareDescription(d1, d2, path)

      // TODO: Add more cases as we expand testing
      case _ =>
        // For now, just check that they're the same type and location
        List(Success(s"$path (${original.getClass.getSimpleName})"))
    }
  }

  private def compareLocation(loc1: At, loc2: At, path: String): List[ComparisonResult] = {
    val results = scala.collection.mutable.ListBuffer[ComparisonResult]()

    // Compare source origin
    if loc1.source.origin != loc2.source.origin then
      results += Failure(path + ".loc.origin", loc1.source.origin, loc2.source.origin)
    end if

    // Compare offsets, not line/col. BAST preserves offsets exactly; line and col are DERIVED
    // from an offset against the attached source, and these round trips deserialise without
    // supplying one, so both report 0 (unknown) by design -- see At.line and
    // RiddlParserInput.positionsKnown. Pass the sources to BASTReader.read and they resolve
    // exactly; BASTLocationFidelityTest pins all three cases.
    //
    // This comment previously said line/col "differ from the original source's real line breaks",
    // which was a defect notice written down and lived with: the reader was decoding real offsets
    // as synthetic ones and producing confident wrong numbers.
    if loc1.offset != loc2.offset then
      results += Failure(path + ".loc.offset", loc1.offset.toString, loc2.offset.toString)
    end if

    if loc1.endOffset != loc2.endOffset then
      results += Failure(path + ".loc.endOffset", loc1.endOffset.toString, loc2.endOffset.toString)
    end if

    results.toList
  }

  private def compareIdentifier(
    id1: Identifier,
    id2: Identifier,
    path: String
  ): List[ComparisonResult] = {
    if id1.value != id2.value then List(Failure(path + ".id.value", id1.value, id2.value))
    else compareLocation(id1.loc, id2.loc, path + ".id") :+ Success(path + ".id")
    end if
  }

  private def compareContents[T <: RiddlValue](
    contents1: Contents[T],
    contents2: Contents[T],
    path: String
  ): List[ComparisonResult] = {
    val seq1 = contents1.toSeq
    val seq2 = contents2.toSeq

    if seq1.size != seq2.size then
      return List(
        Failure(
          path + ".contents.size",
          seq1.size.toString,
          seq2.size.toString,
          s"Content counts differ"
        )
      )
    end if

    // Recursively compare each element
    seq1
      .zip(seq2)
      .zipWithIndex
      .flatMap { case ((item1, item2), idx) =>
        compare(item1, item2, s"$path.contents[$idx]")
      }
      .toList
  }

  /** Compare the optional ascribed streamlet shape carried by every Processor.
    *
    * `ascribedShape` participates in `Definition.equals`, so BAST/JSON round-trips must preserve
    * it. Compared by keyword (the loc is normalized to At.empty on every surface, so it is not a
    * discriminator).
    */
  private def compareAscribedShape(
    s1: Option[StreamletShape],
    s2: Option[StreamletShape],
    path: String
  ): List[ComparisonResult] = {
    (s1, s2) match
      case (None, None) => List(Success(path + ".ascribedShape (absent)"))
      case (Some(a), Some(b)) if a.keyword == b.keyword =>
        List(Success(path + s".ascribedShape (${a.keyword})"))
      case (Some(a), Some(b)) => List(Failure(path + ".ascribedShape", a.keyword, b.keyword))
      case (Some(a), None)    => List(Failure(path + ".ascribedShape", a.keyword, "None"))
      case (None, Some(b))    => List(Failure(path + ".ascribedShape", "None", b.keyword))
  }

  /** Compare a Context's optional intention, which also participates in equality. */
  private def compareIntention(
    i1: Option[Intention],
    i2: Option[Intention],
    path: String
  ): List[ComparisonResult] = {
    (i1, i2) match
      case (None, None)                 => List(Success(path + ".intention (absent)"))
      case (Some(a), Some(b)) if a == b => List(Success(path + s".intention (${a.keyword})"))
      case (Some(a), Some(b))           => List(Failure(path + ".intention", a.keyword, b.keyword))
      case (Some(a), None)              => List(Failure(path + ".intention", a.keyword, "None"))
      case (None, Some(b))              => List(Failure(path + ".intention", "None", b.keyword))
  }

  private def compareDomain(d1: Domain, d2: Domain, path: String): List[ComparisonResult] = {
    compareLocation(d1.loc, d2.loc, path) ++
      compareIdentifier(d1.id, d2.id, path) ++
      compareContents(d1.contents, d2.contents, path) ++
      compareContents(d1.metadata, d2.metadata, path + ".metadata") :+
      Success(path + " (Domain)")
  }

  private def compareContext(c1: Context, c2: Context, path: String): List[ComparisonResult] = {
    compareLocation(c1.loc, c2.loc, path) ++
      compareIdentifier(c1.id, c2.id, path) ++
      compareContents(c1.contents, c2.contents, path) ++
      compareAscribedShape(c1.ascribedShape, c2.ascribedShape, path) ++
      compareIntention(c1.intention, c2.intention, path) ++
      compareContents(c1.metadata, c2.metadata, path + ".metadata") :+
      Success(path + " (Context)")
  }

  private def compareEntity(e1: Entity, e2: Entity, path: String): List[ComparisonResult] = {
    compareLocation(e1.loc, e2.loc, path) ++
      compareIdentifier(e1.id, e2.id, path) ++
      compareContents(e1.contents, e2.contents, path) ++
      compareAscribedShape(e1.ascribedShape, e2.ascribedShape, path) ++
      compareContents(e1.metadata, e2.metadata, path + ".metadata") :+
      Success(path + " (Entity)")
  }

  private def compareStreamlet(
    s1: Streamlet,
    s2: Streamlet,
    path: String
  ): List[ComparisonResult] = {
    compareLocation(s1.loc, s2.loc, path) ++
      compareIdentifier(s1.id, s2.id, path) ++
      compareContents(s1.contents, s2.contents, path) ++
      compareAscribedShape(s1.ascribedShape, s2.ascribedShape, path) ++
      compareContents(s1.metadata, s2.metadata, path + ".metadata") :+
      Success(path + " (Streamlet)")
  }

  private def compareProjector(
    p1: Projector,
    p2: Projector,
    path: String
  ): List[ComparisonResult] = {
    compareLocation(p1.loc, p2.loc, path) ++
      compareIdentifier(p1.id, p2.id, path) ++
      compareContents(p1.contents, p2.contents, path) ++
      compareAscribedShape(p1.ascribedShape, p2.ascribedShape, path) ++
      compareContents(p1.metadata, p2.metadata, path + ".metadata") :+
      Success(path + " (Projector)")
  }

  private def compareRepository(
    r1: Repository,
    r2: Repository,
    path: String
  ): List[ComparisonResult] = {
    compareLocation(r1.loc, r2.loc, path) ++
      compareIdentifier(r1.id, r2.id, path) ++
      compareContents(r1.contents, r2.contents, path) ++
      compareAscribedShape(r1.ascribedShape, r2.ascribedShape, path) ++
      compareContents(r1.metadata, r2.metadata, path + ".metadata") :+
      Success(path + " (Repository)")
  }

  private def compareModule(m1: Module, m2: Module, path: String): List[ComparisonResult] = {
    compareLocation(m1.loc, m2.loc, path) ++
      compareIdentifier(m1.id, m2.id, path) ++
      compareContents(m1.contents, m2.contents, path) ++
      compareContents(m1.metadata, m2.metadata, path + ".metadata") :+
      Success(path + " (Module)")
  }

  private def compareType(t1: Type, t2: Type, path: String): List[ComparisonResult] = {
    compareLocation(t1.loc, t2.loc, path) ++
      compareIdentifier(t1.id, t2.id, path) ++
      // TODO: Compare type expression
      compareContents(t1.metadata, t2.metadata, path + ".metadata") :+
      Success(path + " (Type)")
  }

  private def compareAdaptor(a1: Adaptor, a2: Adaptor, path: String): List[ComparisonResult] = {
    compareLocation(a1.loc, a2.loc, path) ++
      compareIdentifier(a1.id, a2.id, path) ++
      compareContents(a1.contents, a2.contents, path) ++
      compareAscribedShape(a1.ascribedShape, a2.ascribedShape, path) ++
      compareContents(a1.metadata, a2.metadata, path + ".metadata") :+
      Success(path + " (Adaptor)")
  }

  private def compareHandler(h1: Handler, h2: Handler, path: String): List[ComparisonResult] = {
    compareLocation(h1.loc, h2.loc, path) ++
      compareIdentifier(h1.id, h2.id, path) ++
      compareContents(h1.contents, h2.contents, path) ++
      compareContents(h1.metadata, h2.metadata, path + ".metadata") :+
      Success(path + " (Handler)")
  }

  private def compareFunction(f1: Function, f2: Function, path: String): List[ComparisonResult] = {
    compareLocation(f1.loc, f2.loc, path) ++
      compareIdentifier(f1.id, f2.id, path) ++
      compareContents(f1.contents, f2.contents, path) ++
      compareContents(f1.metadata, f2.metadata, path + ".metadata") :+
      Success(path + " (Function)")
  }

  private def compareComment(c1: Comment, c2: Comment, path: String): List[ComparisonResult] = {
    // Comments don't have much structure - just check location
    compareLocation(c1.loc, c2.loc, path) :+
      Success(path + " (Comment)")
  }

  private def compareDescription(
    d1: Description,
    d2: Description,
    path: String
  ): List[ComparisonResult] = {
    val results = compareLocation(d1.loc, d2.loc, path).toBuffer

    // Compare lines
    if d1.lines.size != d2.lines.size then
      results += Failure(
        path + ".lines.size",
        d1.lines.size.toString,
        d2.lines.size.toString
      )
    else
      d1.lines.zip(d2.lines).zipWithIndex.foreach { case ((line1, line2), idx) =>
        if line1.s != line2.s then
          results += Failure(
            path + s".lines[$idx]",
            line1.s,
            line2.s
          )
        end if
      }
    end if

    results.toList :+ Success(path + " (Description)")
  }

  /** Compare Root and Module contents
    *
    * Note: Root is serialized as a Module node, so we compare their contents
    */
  def compareRootAndModule(root: Root, module: Module): List[ComparisonResult] = {
    // Compare top-level contents
    compareContents(
      root.contents.asInstanceOf[Contents[RiddlValue]],
      module.contents.asInstanceOf[Contents[RiddlValue]],
      "root"
    )
  }

  /** Generate a summary report of comparison results */
  def report(results: List[ComparisonResult]): String = {
    val failures = results.filter(!_.isSuccess)
    val successes = results.filter(_.isSuccess)

    val sb = new StringBuilder
    sb.append(s"\n=== Deep AST Comparison Report ===\n")
    sb.append(s"Total comparisons: ${results.size}\n")
    sb.append(s"Successes: ${successes.size}\n")
    sb.append(s"Failures: ${failures.size}\n")

    if failures.nonEmpty then
      sb.append(s"\n--- Failures ---\n")
      failures.foreach { f =>
        sb.append(s"${f.format}\n")
      }
    end if

    // Show first few successes as samples
    if successes.nonEmpty then
      sb.append(s"\n--- Sample Successes (first 10) ---\n")
      successes.take(10).foreach { s =>
        sb.append(s"${s.format}\n")
      }
    end if

    sb.toString
  }
}
