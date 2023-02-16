package com.reactific.riddl.language.validation

import com.reactific.riddl.language.{CommonOptions, Folding, SymbolTable}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At

import scala.annotation.unused
import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

/** Unit Tests For BasicValidationState */
trait BasicValidationState extends Folding.PathResolutionState {

  def symbolTable: SymbolTable
  def root: Definition
  def commonOptions: CommonOptions

  def checkOverloads(): this.type = {
    symbolTable.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
      this.checkSequence(defs) { (s, defs2) =>
        if (defs2.sizeIs == 2) {
          val first = defs2.head
          val last = defs2.last
          s.addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else if (defs2.sizeIs > 2) {
          val first = defs2.head
          val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
          s.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
        } else {
          s
        }
      }
    }
  }

  def parentOf(definition: Definition): Container[Definition] = {
    symbolTable.parentOf(definition).getOrElse(RootContainer.empty)
  }

  def lookup[T <: Definition : ClassTag](id: Seq[String]): List[T] = {
    symbolTable.lookup[T](id)
  }

  def addIf(predicate: Boolean)(msg: => Message): this.type = {
    if (predicate) add(msg) else this
  }

  private val vowels: Regex = "[aAeEiIoOuU]".r

  def article(thing: String): String = {
    val article = if (vowels.matches(thing.substring(0, 1))) "an" else "a"
    s"$article $thing"
  }


  def check(
    predicate: Boolean = true,
    message: => String,
    kind: KindOfMessage,
    loc: At
  ): this.type = {
    if (!predicate) {
      add(Message(loc, message, kind))
    }
    else {
      this
    }
  }

  def checkThat(predicate: Boolean)(f: this.type => this.type): this.type = {
    if (predicate) {
      f(this)
    }
    else {
      this
    }
  }

  def checkSequence[A](elements: Seq[A])(check: (this.type, A) => this.type): this.type = {
    elements.foldLeft[this.type](this) { case (next: this.type , element) => check(next, element) }
  }

  def checkIdentifierLength[T <: Definition](d: T, min: Int = 3): this.type = {
    if (d.id.value.nonEmpty && d.id.value.length < min) {
      addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    } else {
      this
    }
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): this.type = {
    check(
      value.nonEmpty,
      message =
        s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} ${if (required) "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  private def formatDefinitions[T <: Definition](
    list: List[(T, SymbolTable#Parents)]
  ): String = {
    list.map { case (definition, parents) =>
      "  " + parents.reverse.map(_.id.value).mkString(".") + "." +
        definition.id.value + " (" + definition.loc + ")"
    }.mkString("\n")
  }


  type SingleMatchValidationFunction = (
    /* state:*/ this.type,
    /* expectedClass:*/ Class[?],
    /* pathIdSought:*/ PathIdentifier,
    /* foundClass*/ Class[? <: Definition],
    /* definitionFound*/ Definition
    ) => this.type

  type MultiMatchValidationFunction = (
    /* state:*/ this.type,
    /* pid: */ PathIdentifier,
    /* list: */ List[(Definition, Seq[Definition])]
    ) => Seq[Definition]

  val nullSingleMatchingValidationFunction: SingleMatchValidationFunction =
    (state, _, _, _, _) => {
      state
    }

  def defaultSingleMatchValidationFunction(
    state: this.type,
    expectedClass: Class[?],
    pid: PathIdentifier,
    foundClass: Class[? <: Definition],
    @unused definitionFound: Definition
  ): this.type = {
    state.check(
      expectedClass.isAssignableFrom(foundClass),
      s"'${pid.format}' was expected to be ${article(expectedClass.getSimpleName)} but is " +
        s"${article(foundClass.getSimpleName)}.",
      Error,
      pid.loc
    )
  }

  def defaultMultiMatchValidationFunction[T <: Definition : ClassTag](
    state: this.type,
    pid: PathIdentifier,
    list: List[(Definition, Seq[Definition])]
  ): Seq[Definition] = {
    // Extract all the definitions that were found
    val definitions = list.map(_._1)
    val allDifferent = definitions.map(_.kind).distinct.sizeIs ==
      definitions.size
    val expectedClass = classTag[T].runtimeClass
    if (allDifferent || definitions.head.isImplicit) {
      // pick the one that is the right type or the first one
      list.find(_._1.getClass == expectedClass) match {
        case Some((defn, parents)) => defn +: parents
        case None => list.head._1 +: list.head._2
      }
    } else {
      state.addError(
        pid.loc,
        s"""Path reference '${pid.format}' is ambiguous. Definitions are:
           |${formatDefinitions(list)}""".stripMargin
      )
      Seq.empty[Definition]
    }
  }
}
