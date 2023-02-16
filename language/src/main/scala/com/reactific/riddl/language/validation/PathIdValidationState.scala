package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages.Error

import scala.annotation.tailrec
import scala.reflect.{ClassTag, classTag}

/** Unit Tests For PathResolverValidationState */
trait PathIdValidationState extends UsageValidationState {

  private def notResolved[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    kind: Option[String]
  ): Unit = {
    val tc = classTag[T].runtimeClass
    val message = s"Path '${pid.format}' was not resolved," +
      s" in ${container.identify}"
    val referTo = if (kind.nonEmpty) kind.get else tc.getSimpleName
    addError(
      pid.loc,
      message + {
        if (referTo.nonEmpty) s", but should refer to ${article(referTo)}"
        else ""
      }
    )
  }

  def checkPathRef[T <: Definition : ClassTag](
    pid: PathIdentifier,
    container: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  )(single: SingleMatchValidationFunction = defaultSingleMatchValidationFunction
  )(multi: MultiMatchValidationFunction = defaultMultiMatchValidationFunction
  ): this.type = {
    val tc = classTag[T].runtimeClass
    if (pid.value.isEmpty) {
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      addError(pid.loc, message)
    } else {
      val pars =
        if (parents.head != container) container +: parents else parents
      val result = resolvePath(pid, pars) { definitions =>
        if (definitions.nonEmpty) {
          val d = definitions.head
          associateUsage(container, d)
          single(this, tc, pid, d.getClass, d)
          definitions
        } else {
          definitions
        } // signal not resolved below
      } { list => multi(this, pid, list) }
      if (result.isEmpty) {
        notResolved(pid, container, kind)
      }
    }
    this
  }

  def checkRef[T <: Definition : ClassTag](
    reference: Reference[T],
    defn: Definition,
    parents: Seq[Definition],
    kind: Option[String] = None
  ): this.type = {
    checkPathRef[T](reference.pathId, defn, parents, kind)()()
  }

  def checkMessageRef(
    ref: MessageRef,
    topDef: Definition,
    parents: Seq[Definition],
    kind: AggregateUseCase
  ): this.type = {
    if (ref.isEmpty) {
      addError(ref.pathId.loc, s"${ref.identify} is empty")
    }
    else {
      checkPathRef[Type](ref.pathId, topDef, parents, Some(kind.kind)) {
        (state, _, _, _, defn) =>
          defn match {
            case Type(_, _, typ, _, _) => typ match {
              case AggregateUseCaseTypeExpression(_, mk, _) => state.check(
                mk == kind,
                s"'${ref.identify} should be ${article(kind.kind)} type" +
                  s" but is ${article(mk.kind)} type instead",
                Error,
                ref.pathId.loc
              )
              case te: TypeExpression => state.addError(
                ref.pathId.loc,
                s"'${ref.identify} should reference ${article(kind.kind)} but is a ${
                  AST
                    .errorDescription(te)
                } type instead"
              )
            }
            case _ => state.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be ${article(kind.kind)} type but is ${article(defn.kind)} instead"
            )
          }
      }(defaultMultiMatchValidationFunction)
    }
  }


  @tailrec final def getPathIdType(
    pid: PathIdentifier,
    parents: Seq[Definition]
  ): Option[TypeExpression] = {
    if (pid.value.isEmpty) {
      None
    }
    else {
      val newParents: Seq[Definition] = resolvePath(pid, parents)()()
      val candidate: Option[TypeExpression] = newParents.headOption match {
        case None => None
        case Some(f: Function) => f.output
        case Some(t: Type) => Some(t.typ)
        case Some(f: Field) => Some(f.typeEx)
        case Some(s: State) => Some(s.aggregation)
        case Some(Pipe(_, _, _, tt, _, _, _, _)) =>
          val te = tt.map(x => AliasedTypeExpression(x.loc, x.pathId))
          Some(te.getOrElse(Abstract(pid.loc)))
        case Some(Inlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(Outlet(_, _, typ, _, _)) =>
          Some(AliasedTypeExpression(typ.loc, typ.pathId))
        case Some(_) => Option.empty[TypeExpression]
      }
      candidate match {
        case Some(AliasedTypeExpression(_, pid)) =>
          getPathIdType(pid, newParents)
        case Some(other: TypeExpression) => Some(other)
        case None => None
      }
    }
  }
}
