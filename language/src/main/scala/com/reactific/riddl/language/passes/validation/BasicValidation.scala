package com.reactific.riddl.language.passes.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.passes.resolution.ResolutionOutput
import com.reactific.riddl.language.{AST, Messages, SymbolTable}

import scala.annotation.{tailrec, unused}
import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

/** Unit Tests For BasicValidationState */
trait BasicValidation  {

  def resolution: ResolutionOutput

  def messages: Messages.Accumulator

  def parentOf(definition: Definition): Container[Definition] = {
    resolution.symbols.parentOf(definition).getOrElse(RootContainer.empty)
  }

  def lookup[T <: Definition : ClassTag](id: Seq[String]): List[T] = {
    resolution.symbols.lookup[T](id)
  }

  def pathIdToDefinition(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[Definition] = {
    resolution.refMap.definitionOf(pid, parents.head)
  }

  @inline def resolvePath[T <: Definition](
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[T] = {
    pathIdToDefinition(pid, parents).map(_.asInstanceOf[T])
  }

  def resolvePidRelativeTo[T <: Definition](
    pid: PathIdentifier,
    relativeTo: Definition
  ): Option[T] = {
    val parents = relativeTo +: resolution.symbols.parentsOf(relativeTo)
    resolvePath[T](pid, parents)
  }

  private def notResolved[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    kind: Option[String]
  ): Unit = {
    val tc = classTag[T].runtimeClass
    val message = s"Path '${pid.format}' was not resolved, in ${container.identify}"
    val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName
    messages.addError(
      pid.loc,
      {
        if (referTo.nonEmpty) s"$message, but should refer to ${article(referTo)}"
        else message
      }
    )
  }

  def checkPathRef[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  ): Option[T] = {
    val tc = classTag[T].runtimeClass
    if (pid.value.isEmpty) {
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      messages.addError(pid.loc, message)
      Option.empty[T]
    } else {
      val pars = if (parents.toSeq.head != container) container +: parents else parents
      resolvePath[T](pid, pars.toSeq) match {
        case None =>
          notResolved(pid, container, kind)
          Option.empty[T]
        case t: Option[T] => t
      }
    }
  }

  def checkRef[T <: Definition : ClassTag](
    reference: Reference[T],
    definition: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  ): Option[T] = {
    checkPathRef[T](reference.pathId, definition, parents, kind)
  }

  def checkRefAndExamine[T <: Definition : ClassTag](
    reference: Reference[T],
    defn: Definition,
    parents: Seq[Definition]
  )(examiner: T => Unit): this.type = {
    checkPathRef[T](reference.pathId, defn, parents, None).map { resolved: T =>
      resolved match {
        case t: T => examiner(t)
        case _ => assert(classTag[T].runtimeClass == resolved.getClass)
      }
    }
    this
  }

  def checkMaybeRef[T <: Definition : ClassTag](
    reference: Option[Reference[T]],
    defn: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  ): this.type = {
    reference.map { ref =>
      checkPathRef[T](ref.pathId, defn, parents, kind)
    }
    this
  }

  def checkMessageRef(
    ref: MessageRef,
    topDef: Definition,
    parents: Seq[Definition],
    kind: AggregateUseCase
  ): this.type = {
    if (ref.isEmpty) {
      messages.addError(ref.pathId.loc, s"${ref.identify} is empty")
      this
    } else {
      checkRefAndExamine[Type](ref, topDef, parents) { definition: Definition =>
        definition match {
          case Type(_, _, typ, _, _) =>
            typ match {
              case AggregateUseCaseTypeExpression(_, mk, _) =>
                check(
                  mk == kind,
                  s"'${ref.identify} should be ${article(kind.kind)} type" +
                    s" but is ${article(mk.kind)} type instead",
                  Error,
                  ref.pathId.loc
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference ${article(kind.kind)} but is a ${
                    AST.errorDescription(te)
                  } type instead"
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(definition.kind)} instead"
            )
        }
      }
    }
  }

  @tailrec final def getPathIdType(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    if (pid.value.isEmpty) {
      None
    } else {
      val maybeDef: Option[Definition] = resolvePath(pid, parents)
      val candidate: Option[TypeExpression] = maybeDef.headOption match {
        case None => None
        case Some(f: Function) => f.output
        case Some(t: Type) => Some(t.typ)
        case Some(f: Field) => Some(f.typeEx)
        case Some(s: State) =>
          Some(AliasedTypeExpression(s.typ.loc, s.typ.pathId))
        case Some(Inlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(Outlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(connector: Connector) =>
          connector.flows
            .map(typeRef => AliasedTypeExpression(typeRef.loc, typeRef.pathId))
            .orElse(Option.empty[TypeExpression])
        case Some(streamlet: Streamlet) if streamlet.outlets.size == 1 =>
            resolvePath[Type](
              streamlet.outlets.head.type_.pathId,
              parents.toSeq
            )
            .map(_.typ)
        case Some(_) => Option.empty[TypeExpression]
      }
      candidate match {
        case Some(AliasedTypeExpression(_, pid)) =>
          getPathIdType(pid, maybeDef.toSeq)
        case Some(other: TypeExpression) => Some(other)
        case None => None
      }
    }
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
    if (!predicate)
      messages.add(Message(loc, message, kind))
    this
  }

  def checkWhen(predicate: Boolean)(checker: () => Unit): this.type = {
    if (predicate) checker()
    this
  }

  def checkSequence[A](elements: Seq[A])(check: (A) => Unit): this.type = {
    elements.foreach(check(_))
    this
  }

  def checkOverloads(): this.type = {
    resolution.symbols.foreachOverloadedSymbol { defs: Seq[Seq[Definition]] =>
      this.checkSequence(defs) { defs2 =>
        val first = defs2.head
        if (defs2.sizeIs == 2) {
          val last = defs2.last
          messages.addStyle(
            last.loc,
            s"${last.identify} overloads ${first.identifyWithLoc}"
          )
        } else {
          if (defs2.sizeIs > 2) {
            val tail = defs2.tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
            messages.addStyle(first.loc, s"${first.identify} overloads:\n  $tail")
          }
        }
      }
    }
    this
  }



  def checkIdentifierLength[T <: Definition](d: T, min: Int = 3): this.type = {
    if (d.id.value.nonEmpty && d.id.value.length < min) {
      messages.addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    }
    this
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
    /* expectedClass:*/ Class[?],
    /* pathIdSought:*/ PathIdentifier,
    /* foundClass*/ Class[? <: Definition],
    /* definitionFound*/ Definition
    ) => Unit

  type MultiMatchValidationFunction = (
    /* pid: */ PathIdentifier,
    /* list: */ List[(Definition, Seq[Definition])]
    ) => Seq[Definition]

  val nullSingleMatchingValidationFunction: SingleMatchValidationFunction = ( _, _, _, _) => { () }

  def defaultSingleMatchValidationFunction(
    expectedClass: Class[?],
    pid: PathIdentifier,
    foundClass: Class[? <: Definition],
    @unused definitionFound: Definition
  ): this.type = {
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
      messages.addError(
        pid.loc,
        s"""Path reference '${pid.format}' is ambiguous. Definitions are:
           |${formatDefinitions(list)}""".stripMargin
      )
      Seq.empty[Definition]
    }
  }
}
