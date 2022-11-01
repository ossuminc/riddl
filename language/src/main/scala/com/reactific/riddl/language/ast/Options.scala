/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.ast
import scala.reflect.ClassTag

/** Option definitions for Vitals */
trait Options extends AbstractDefinitions {

  /** Base trait for option values for any option of a definition.
    */
  trait OptionValue extends RiddlValue {
    def name: String

    def args: Seq[LiteralString] = Seq.empty[LiteralString]

    override def format: String = name + args.map(_.format)
      .mkString("(", ", ", ")")
  }

  /** Base trait that can be used in any definition that takes options and
    * ensures the options are defined, can be queried, and formatted.
    *
    * @tparam T
    *   The sealed base trait of the permitted options for this definition
    */
  trait WithOptions[T <: OptionValue] extends Definition {
    def options: Seq[T]

    def hasOption[OPT <: T: ClassTag]: Boolean = options
      .exists(_.getClass == implicitly[ClassTag[OPT]].runtimeClass)

    def getOptionValue[OPT <: T: ClassTag]: Option[Seq[LiteralString]] = options
      .find(_.getClass == implicitly[ClassTag[OPT]].runtimeClass).map(_.args)

    override def format: String = {
      options.size match {
        case 0 => ""
        case 1 => s"option is ${options.head.format}"
        case x: Int if x > 1 =>
          s"options ( ${options.map(_.format).mkString(" ", ", ", " )")}"
      }
    }

    override def isEmpty: Boolean = options.isEmpty && super.isEmpty

    override def hasOptions: Boolean = true
  }

  //////////////////////////////////////////////////////////////////// ADAPTOR

  sealed abstract class AdaptorOption(val name: String) extends OptionValue

  case class AdaptorTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends AdaptorOption("technology")

  //////////////////////////////////////////////////////////////////// HANDLER

  sealed abstract class HandlerOption(val name: String) extends OptionValue

  case class PartialHandlerOption(loc: Location)
      extends HandlerOption("partial")

  /////////////////////////////////////////////////////////////////// PROJECTION

  sealed abstract class ProjectionOption(val name: String) extends OptionValue

  case class ProjectionTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends ProjectionOption("technology")

  /////////////////////////////////////////////////////////////////// PROJECTION

  sealed abstract class RepositoryOption(val name: String) extends OptionValue

  case class RepositoryTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends RepositoryOption("technology")

  //////////////////////////////////////////////////////////////////// ENTITY

  /** Base trait of any value used in the definition of an entity
    */
  sealed trait EntityValue extends RiddlValue

  /** Abstract base class of options for entities
    *
    * @param name
    *   the name of the option
    */
  sealed abstract class EntityOption(val name: String)
      extends EntityValue with OptionValue

  /** An [[EntityOption]] that indicates that this entity should store its state
    * in an event sourced fashion.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityEventSourced(loc: Location)
      extends EntityOption("event sourced")

  /** An [[EntityOption]] that indicates that this entity should store only the
    * latest value without using event sourcing. In other words, the history of
    * changes is not stored.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityValueOption(loc: Location) extends EntityOption("value")

  /** An [[EntityOption]] that indicates that this entity should not persist its
    * state and is only available in transient memory. All entity values will be
    * lost when the service is stopped.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityTransient(loc: Location) extends EntityOption("transient")

  /** An [[EntityOption]] that indicates that this entity is an aggregate root
    * entity through which all commands and queries are sent on behalf of the
    * aggregated entities.
    *
    * @param loc
    *   The location of the option
    */
  case class EntityIsAggregate(loc: Location) extends EntityOption("aggregate")

  /** An [[EntityOption]] that indicates that this entity favors consistency
    * over availability in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsConsistent(loc: Location)
      extends EntityOption("consistent")

  /** A [[EntityOption]] that indicates that this entity favors availability
    * over consistency in the CAP theorem.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsAvailable(loc: Location) extends EntityOption("available")

  /** An [[EntityOption]] that indicates that this entity is intended to
    * implement a finite state machine.
    *
    * @param loc
    *   The location of the option.
    */
  case class EntityIsFiniteStateMachine(loc: Location)
      extends EntityOption("finite state machine")

  /** An [[EntityOption]] that indicates that this entity should allow receipt
    * of commands and queries via a message queue.
    *
    * @param loc
    *   The location at which this option occurs.
    */
  case class EntityMessageQueue(loc: Location)
      extends EntityOption("message queue")

  case class EntityTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends EntityOption("technology")

  /** An [[EntityOption]] that indicates the general kind of entity being
    * defined. This option takes a value which provides the kind. Examples of
    * useful kinds are "device", "actor", "concept", "machine", and similar
    * kinds of entities. This entity option may be used by downstream AST
    * processors, especially code generators.
    *
    * @param loc
    *   The location of the entity kind option
    * @param args
    *   The argument to the option
    */
  case class EntityKind(loc: Location, override val args: Seq[LiteralString])
      extends EntityOption("kind")

  //////////////////////////////////////////////////////////////////// FUNCTION

  /** Base class of all function options
    *
    * @param name
    *   The name of the option
    */
  sealed abstract class FunctionOption(val name: String) extends OptionValue

  /** A function option to mark a function as being tail recursive
    * @param loc
    *   The location of the tail recursive option
    */
  case class TailRecursive(loc: Location)
      extends FunctionOption("tail-recursive")

  //////////////////////////////////////////////////////////////////// CONTEXT

  /** Base trait for all options a Context can have.
    */
  sealed abstract class ContextOption(val name: String) extends OptionValue

  case class ContextPackageOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends ContextOption("package")

  /** A context's "wrapper" option. This option suggests the bounded context is
    * to be used as a wrapper around an external system and is therefore at the
    * boundary of the context map
    *
    * @param loc
    *   The location of the wrapper option
    */
  case class WrapperOption(loc: Location) extends ContextOption("wrapper")

  /** A context's "service" option. This option suggests the bounded context is
    * intended to be a DDD service, similar to a wrapper but without any
    * persistent state and more of a stateless service aspect to its nature
    *
    * @param loc
    *   The location at which the option occurs
    */
  case class ServiceOption(loc: Location) extends ContextOption("service")

  /** A context's "gateway" option that suggests the bounded context is intended
    * to be an application gateway to the model. Gateway's provide
    * authentication and authorization access to external systems, usually user
    * applications.
    *
    * @param loc
    *   The location of the gateway option
    */
  case class GatewayOption(loc: Location) extends ContextOption("gateway")

  case class ContextTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends ContextOption("technology")

  //////////////////////////////////////////////////////////////////// PROCESSOR

  sealed abstract class ProcessorOption(val name: String) extends OptionValue

  case class ProcessorTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends ProcessorOption("technology")

  //////////////////////////////////////////////////////////////////// PLANT

  sealed abstract class PlantOption(val name: String) extends OptionValue

  case class PlantPackageOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends PlantOption("package")

  case class PlantTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends PlantOption("technology")

  //////////////////////////////////////////////////////////////////// SAGA

  /** Base trait for all options applicable to a saga.
    */
  sealed abstract class SagaOption(val name: String) extends OptionValue

  /** A [[SagaOption]] that indicates sequential (serial) execution of the saga
    * actions.
    *
    * @param loc
    *   The location of the sequential option
    */
  case class SequentialOption(loc: Location) extends SagaOption("sequential")

  /** A [[SagaOption]] that indicates parallel execution of the saga actions.
    *
    * @param loc
    *   The location of the parallel option
    */
  case class ParallelOption(loc: Location) extends SagaOption("parallel")

  case class SagaTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends SagaOption("technology")

  ////////////////////////////////////////////////////////////////// APPLICATION

  sealed abstract class ApplicationOption(val name: String) extends OptionValue

  case class ApplicationTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString] = Seq.empty[LiteralString])
      extends ApplicationOption("technology")

  ////////////////////////////////////////////////////////////////// DOMAIN

  /** Base trait for all options a Domain can have.
    */
  sealed abstract class DomainOption(val name: String) extends OptionValue

  /** A context's "wrapper" option. This option suggests the bounded context is
    * to be used as a wrapper around an external system and is therefore at the
    * boundary of the context map
    *
    * @param loc
    *   The location of the wrapper option
    */
  case class DomainPackageOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends DomainOption("package")

  case class DomainExternalOption(loc: Location)
      extends DomainOption("external")

  case class DomainTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends DomainOption("technology")

  ////////////////////////////////////////////////////////////////// DOMAIN

  sealed abstract class StoryOption(val name: String) extends OptionValue

  case class StoryTechnologyOption(
    loc: Location,
    override val args: Seq[LiteralString])
      extends StoryOption("technology")

  case class StorySynchronousOption(loc: Location) extends StoryOption("synch")

}
