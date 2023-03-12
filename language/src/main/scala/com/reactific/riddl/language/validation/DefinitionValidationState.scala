package com.reactific.riddl.language.validation

import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.AST.*

import scala.math.abs

/** Unit Tests For DefinitionValidationState */
trait DefinitionValidationState extends BasicValidationState {

  def checkOptions[T <: OptionValue](options: Seq[T], loc: At): Unit = {
    check(
      options.sizeIs == options.distinct.size,
      "Options should not be repeated",
      Error,
      loc
    )
  }

  def checkOption[A <: RiddlValue](
    opt: Option[A],
    name: String,
    thing: Definition
  )(folder: (A) => Unit): Unit = {
    opt match {
      case None =>
        addMissing(thing.loc, s"$name in ${thing.identify} should not be empty")
      case Some(x) =>
        checkNonEmptyValue(x, "Condition", thing, MissingWarning)
        folder(x)
    }
  }

  private def checkUniqueContent(definition: Definition): this.type = {
    val allNames = definition.contents.map(_.id.value)
    val uniqueNames = allNames.toSet
    if (allNames.size != uniqueNames.size) {
      val duplicateNames = allNames.toSet.removedAll(uniqueNames)
      addError(
        definition.loc,
        s"${definition.identify} has duplicate content names:\n${duplicateNames
            .mkString("  ", ",\n  ", "\n")}"
      )
    } else {
      this
    }
  }

  def checkDefinition(
    parents: Seq[Definition],
    definition: Definition
  ): Unit = {
    check(
      definition.id.nonEmpty | definition.isImplicit,
      "Definitions may not have empty names",
      Error,
      definition.loc
    )
    .checkIdentifierLength(definition)
    .checkUniqueContent(definition)
    .check(
      !definition.isVital || definition.hasAuthors,
      "Vital definitions should have an author reference",
      MissingWarning,
      definition.loc
    )

    if (definition.isVital) {
      definition match {
        case vd: VitalDefinition[?, ?] =>
          val authorRefs: Seq[AuthorRef] = vd.authors
          authorRefs.foldLeft(vs1) {
            case (vs, authorRef) =>
              pathIdToDefinition(authorRef.pathId, parents) match {
                case None =>
                  vs.addError(
                    authorRef.loc,
                    s"${authorRef.format} is not defined"
                  )
                case _ => vs
              }
            case (_, _) => vs1
          }
        case _ => vs1
      }
    }

    val path = symbolTable.pathOf(definition)
    if (!definition.id.isEmpty) {
      val matches = vs1.lookup[Definition](path)
      if (matches.isEmpty) {
        vs1 = vs1.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!"
        )
      } else if (matches.sizeIs >= 2) {
        val parentGroups = matches.groupBy(vs1.symbolTable.parentOf(_))
        parentGroups.get(parents.headOption) match {
          case Some(head :: tail) if tail.nonEmpty =>
            vs1 = vs1.addWarning(
              head.id.loc,
              s"${definition.identify} has same name as other definitions " +
                s"in ${head.identifyWithLoc}:  " +
                tail.map(x => x.identifyWithLoc).mkString(",  ")
            )
          case Some(head :: tail) if tail.isEmpty =>
            vs1 = vs1.addStyle(
              head.id.loc,
              s"${definition.identify} has same name as other definitions: " +
                matches
                  .filterNot(_ == definition)
                  .map(x => x.identifyWithLoc)
                  .mkString(",  ")
            )
          case _ =>
          // ignore
        }
      }
    }
    vs1
  }

  def checkContainer(
    parents: Seq[Definition],
    container: Definition
  ): this.type = {
    val parent: Definition = parents.headOption.getOrElse(RootContainer.empty)
    checkDefinition(parents, container).check(
      container.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  }

  def checkDescription[TD <: DescribedValue](
    id: String,
    value: TD
  ): this.type = {
    val description: Option[Description] = value.description
    val shouldCheck: Boolean = {
      value.isInstanceOf[Type] |
        (value.isInstanceOf[Definition] && value.nonEmpty)
    }
    if (description.isEmpty && shouldCheck) {
      this.check(
        predicate = false,
        s"$id should have a description",
        MissingWarning,
        value.loc
      )
    } else if (description.nonEmpty) {
      val desc = description.get
      this.check(
        desc.nonEmpty,
        s"For $id, description at ${desc.loc} is declared but empty",
        MissingWarning,
        desc.loc
      )
    } else this
  }

  def checkDescription[TD <: Definition](
    definition: TD
  ): this.type = {
    checkDescription(definition.identify, definition)
  }

  def checkStreamletShape(proc: Streamlet): this.type = {
    val ins = proc.inlets.size
    val outs = proc.outlets.size

    def generateError(
      proc: Streamlet,
      req_ins: Int,
      req_outs: Int
    ): this.type = {
      def sOutlet(n: Int): String = {
        if (n == 1) s"1 outlet"
        else if (n < 0) {
          s"at least ${abs(n)} outlets"
        } else s"$n outlets"
      }

      def sInlet(n: Int): String = {
        if (n == 1) s"1 inlet"
        else if (n < 0) {
          s"at least ${abs(n)} outlets"
        } else s"$n inlets"
      }

      this.addError(
        proc.loc,
        s"${proc.identify} should have " + sOutlet(req_outs) + " and " +
          sInlet(req_ins) + s" but it has " + sOutlet(outs) + " and " +
          sInlet(ins)
      )
    }

    if (!proc.isEmpty) {
      proc.shape match {
        case _: Source =>
          if (ins != 0 || outs != 1) {
            generateError(proc, 0, 1)
          } else {
            this
          }
        case _: Flow =>
          if (ins != 1 || outs != 1) {
            generateError(proc, 1, 1)
          } else {
            this
          }
        case _: Sink =>
          if (ins != 1 || outs != 0) {
            generateError(proc, 1, 0)
          } else {
            this
          }
        case _: Merge =>
          if (ins < 2 || outs != 1) {
            generateError(proc, -2, 1)
          } else {
            this
          }
        case _: Split =>
          if (ins != 1 || outs < 2) {
            generateError(proc, 1, -2)
          } else {
            this
          }
        case _: Router =>
          if (ins < 2 || outs < 2) {
            generateError(proc, -2, -2)
          } else {
            this
          }
        case _: Void =>
          if (ins > 0 || outs > 0) {
            generateError(proc, 0, 0)
          } else {
            this
          }
      }
    } else {
      this
    }
  }

}
