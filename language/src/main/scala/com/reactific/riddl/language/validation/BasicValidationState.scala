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

  def checkOverloads(): Unit = {
    symbolTable.foreachOverloadedSymbol[Unit] { (defs: Seq[Seq[Definition]]) =>
      checkSequence(defs) { (aDef) =>
        if (aDef.sizeIs == 2) {
          val first = aDef.head
          val last = aDef.last
          addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else if (aDef.sizeIs > 2) {
          val first = aDef.head
          val tail = aDef.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
          addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
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

  def addIf(predicate: Boolean)(msg: => Message): Unit = {
    if (predicate) add(msg)
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
  ): Unit = {
    if (!predicate) {
      add(Message(loc, message, kind))
    }
  }

  def checkThat(predicate: Boolean)(f:  => Unit): Unit = {
    if (predicate) {
      f
    }
  }

  def checkSequence[A](elements: Seq[A])(check: (A) => Unit): Unit = {
    elements.foreach { (a: A) => check(a) }
  }

  def checkIdentifierLength[T <: Definition](d: T, min: Int = 3): Unit = {
    if (d.id.value.nonEmpty && d.id.value.length < min) {
      addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    }
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): Unit = {
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
  ): Unit = {
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


  type SingleMatchValidationFunction[T <: Definition] = (
      /* expectedClass:*/ Class[T],
      /* pathIdSought:*/ PathIdentifier,
      /* foundClass*/ Class[? <: Definition],
      /* definitionFound*/ Definition
    ) => Unit

  type MultiMatchValidationFunction = (
      /* pid: */ PathIdentifier,
      /* list: */ List[(Definition, Seq[Definition])]
    ) => Seq[Definition]

  def nullSingleMatchingValidationFunction[T <: Definition]: SingleMatchValidationFunction[T] = {
    (_, _, _, _) => { () }
  }

  def defaultSingleMatchValidationFunction[T <: Definition](
    expectedClass: Class[T],
    pid: PathIdentifier,
    foundClass: Class[? <: Definition],
    @unused definitionFound: Definition
  ): Unit = {
    check(
      expectedClass.isAssignableFrom(foundClass),
      s"'${pid.format}' was expected to be ${article(expectedClass.getSimpleName)} but is " +
        s"${article(foundClass.getSimpleName)}.",
      Error,
      pid.loc
    )
  }

  def defaultMultiMatchValidationFunction[T <: Definition : ClassTag](
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
      addError(
        pid.loc,
        s"""Path reference '${pid.format}' is ambiguous. Definitions are:
           |${formatDefinitions(list)}""".stripMargin
      )
      Seq.empty[Definition]
    }
  }
}

