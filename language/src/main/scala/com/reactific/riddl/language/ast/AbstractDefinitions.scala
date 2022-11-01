/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast

import com.reactific.riddl.language.parsing.Terminals

import java.nio.file.Path
import scala.reflect.ClassTag
import scala.reflect.classTag

trait AbstractDefinitions extends Terminals {

  /** The root trait of all things RIDDL AST. Every node in the tree is a
    * RiddlNode.
    */
  trait RiddlNode {

    /** Format the node to a string */
    def format: String

    /** Determine if this node is a container or not */
    def isContainer: Boolean = false

    /** determine if this node is empty or not. Non-containers are always empty
      */
    def isEmpty: Boolean = false

    @deprecatedOverriding(
      "nonEmpty is defined as !isEmpty; override isEmpty instead"
    ) final def nonEmpty: Boolean = !isEmpty
  }

  /** The root trait of all parsable values. If a parser returns something, its
    * a RiddlValue. The distinguishing factor is the inclusion of the parsing
    * location given by the `loc` field.
    */
  trait RiddlValue extends RiddlNode {

    /** The location in the parse at which this RiddlValue occurs */
    def loc: Location
  }

  /** Represents a literal string parsed between quote characters in the input
    *
    * @param loc
    *   The location in the input of the opening quote character
    * @param s
    *   The parsed value of the string content
    */
  case class LiteralString(loc: Location, s: String) extends RiddlValue {
    override def format = s"\"$s\""

    override def isEmpty: Boolean = s.isEmpty
  }
  object LiteralString {
    val empty: LiteralString = LiteralString(Location.empty, "")
  }

  /** A RiddlValue that is a parsed identifier, typically the name of a
    * definition.
    *
    * @param loc
    *   The location in the input where the identifier starts
    * @param value
    *   The parsed value of the identifier
    */
  case class Identifier(loc: Location, value: String) extends RiddlValue {
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  object Identifier {
    val empty: Identifier = Identifier(Location.empty, "")
  }

  /** Represents a segmented identifier to a definition in the model. Path
    * Identifiers are parsed from a dot-separated list of identifiers in the
    * input. Path identifiers are used to reference other definitions in the
    * model.
    *
    * @param loc
    *   Location in the input of the first letter of the path identifier
    * @param value
    *   The list of strings that make up the path identifier
    */
  case class PathIdentifier(loc: Location, value: Seq[String])
      extends RiddlValue {
    override def format: String = {
      value.foldLeft(Seq.empty[String]) { case (r: Seq[String], s: String) =>
        if (s.isEmpty) { r :+ "^" }
        else if (r.isEmpty) { Seq(s) }
        else if (r.last != "^") { r ++ Seq(".", s) }
        else { r :+ s }
      }.mkString
    }

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  /** The description of a definition. All definitions have a name and an
    * optional description. This class provides the description part.
    */
  trait Description extends RiddlValue {
    def loc: Location

    def lines: Seq[LiteralString]

    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.isEmpty)
  }

  case class BlockDescription(
    loc: Location = Location.empty,
    lines: Seq[LiteralString] = Seq.empty[LiteralString])
      extends Description {
    def format: String = ""
  }

  case class FileDescription(
    loc: Location,
    file: Path)
      extends Description {
    lazy val lines: Seq[LiteralString] = {
      val src = scala.io.Source.fromFile(file.toFile)
      src.getLines().toSeq.map(LiteralString(loc, _))
    }
    def format: String = ""
  }

  trait BrieflyDescribedValue extends RiddlValue {
    def brief: Option[LiteralString]
    def briefValue: String = {
      brief.map(_.s).getOrElse("No brief description.")
    }
  }

  /** Base trait of all values that have an optional Description
    */
  trait DescribedValue extends RiddlValue {
    def description: Option[Description]
    def descriptionValue: String = {
      description.map(_.lines.map(_.s))
        .mkString("", System.lineSeparator(), System.lineSeparator())
    }
  }

  /** Base trait of any definition that is also a ContainerValue
    *
    * @tparam D
    *   The kind of definition that is contained by the container
    */
  trait Container[+D <: RiddlValue] extends RiddlValue {
    def contents: Seq[D]

    override def isEmpty: Boolean = contents.isEmpty

    override def isContainer: Boolean = true

    def isRootContainer: Boolean = false
  }

  /** Base trait for all definitions requiring an identifier for the definition
    * and providing the identify method to yield a string that provides the kind
    * and name
    */
  trait Definition
      extends DescribedValue
      with BrieflyDescribedValue
      with Container[Definition] {
    def id: Identifier

    def kind: String

    def identify: String = {
      if (id.isEmpty) { s"Anonymous $kind" }
      else { s"$kind '${id.format}'" }
    }

    def identifyWithLoc: String = s"$identify at $loc"

    def isImplicit: Boolean = id.value.isEmpty

    def hasOptions: Boolean = false

    def hasAuthors: Boolean = false

    def hasTypes: Boolean = false

    def find(name: String): Option[Definition] = {
      contents.find(_.id.value == name)
    }
  }

  trait LeafDefinition extends Definition {
    override def isEmpty: Boolean = true
    final def contents: Seq[Definition] = Seq.empty[Definition]
  }

  trait AlwaysEmpty extends Definition {
    final override def isEmpty: Boolean = true
  }

  /** A reference to a definition of a specific type.
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  abstract class Reference[+T <: Definition: ClassTag] extends RiddlValue {
    def id: PathIdentifier
    def identify: String = {
      s"Reference[${classTag[T].runtimeClass.getSimpleName}] '${id.format}'${loc.toShort}"
    }
    override def isEmpty: Boolean = id.isEmpty
  }

  /** Base class for all actions. Actions are used in the "then" and "but"
    * clauses of a Gherkin example such as in the body of a handler's `on`
    * clause or in the definition of a Function. The subclasses define different
    * kinds of actions that can be used.
    */
  trait Action extends DescribedValue

  /** An action that can also be used in a SagaStep
    */
  trait SagaStepAction extends Action

  /** Base class of any Gherkin value
    */
  trait GherkinValue extends RiddlValue

  /** Base class of one of the four Gherkin clauses (Given, When, Then, But)
    */
  trait GherkinClause extends GherkinValue

  /** Base trait of any definition that is in the content of a function.
    */
  trait FunctionDefinition extends Definition

  /** Base trait of definitions that are part of a Saga Definition */
  trait SagaDefinition extends Definition

  /** Base trait of definitions that are part of a Saga Definition */
  trait StateDefinition extends Definition

  /** Base trait of any definition that occurs in the body of a projection */
  trait ProjectionDefinition extends Definition

}
