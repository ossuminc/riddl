package com.reactific.riddl.c4
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Folding
import com.reactific.riddl.language.Translator
import com.reactific.riddl.language.Validation
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.utils.Logger
import com.structurizr.model.Enterprise
import com.structurizr.model.InteractionStyle
import com.structurizr.model.Location
import com.structurizr.model.Person
import com.structurizr.model.SoftwareSystem
import com.structurizr.model.StaticStructureElement

import scala.annotation.unused
import scala.collection.mutable

object C4Translator extends Translator[C4Command.Options] {

  override def translate(
    result: Validation.Result,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: C4Command.Options
  ): Either[Messages, C4TranslatorState] = {
    translateImpl(result, commonOptions, options)
  }

  def translateImpl(
    result: Validation.Result,
    commonOptions: CommonOptions,
    options: C4Command.Options
  ): Either[Messages, C4TranslatorState] = {
    require(options.outputRoot.getNameCount > 2, "Output path is too shallow")
    require(
      options.outputRoot.getFileName.toString.nonEmpty,
      "Output path is empty"
    )
    val state = C4TranslatorState(result, options, commonOptions)
    val parentStack = mutable.Stack[Definition]()

    // Build up the model from the various components
    Folding.foldLeftWithStack(state, parentStack)(result.root)(addComponents)
    // Now you can build up the views
    parentStack.clear()
    Right(Folding.foldLeftWithStack(state, parentStack)(result.root)(addViews))
  }

  def pathIdToC4Component(
    state: C4TranslatorState,
    pid: PathIdentifier
  ): StaticStructureElement = {
    val stack = state.resolvePath(pid)()()
    assert(stack.nonEmpty, s"Cannot resolve path: $pid")
    val used = stack.head
    state.elementByDef(used)
  }

  def addComponents(
    state: C4TranslatorState,
    definition: Definition,
    parents: Seq[Definition]
  ): C4TranslatorState = {
    definition match {
      case _: RootContainer =>
        // This is the enterprise level
        val enterpriseName = state.options.enterpriseName
          .getOrElse("Unknown Enterprise Name")
        val enterprise: Enterprise = new Enterprise(enterpriseName)
        state.model.setEnterprise(enterprise)
        state
      case d: Domain =>
        val name = state.makeDefPath(d, parents).mkString(".")
        val location = {
          if (d.hasOption[DomainExternalOption]) { Location.External }
          else { Location.Internal }
        }
        val ss = state.model.addSoftwareSystem(location, name, d.briefValue)
        state.elementByDef.addOne((d, ss))
        state
      case c: Context =>
        require(parents.nonEmpty)
        val domainName = state.makeDefPath(parents.head, parents.tail)
          .mkString(".")
        val ss = state.model.getSoftwareSystemWithName(domainName)
        require(ss != null, "Domains should generate SoftwareSystems")
        val typ: String = {
          if (c.hasOption[WrapperOption]) "Wrapper"
          else if (c.hasOption[ServiceOption]) "Service"
          else if (c.hasOption[GatewayOption]) "Gateway"
          else "Context"
        }
        val name = state.makeDefPath(c, parents).mkString(".")
        val cont = ss.addContainer(name, typ, c.briefValue)
        state.elementByDef.addOne((c, cont))
        state
      case h: Handler =>
        require(parents.length >= 2)
        parents.head match {
          case context: Context =>
            val technology: String =
              context.getOptionValue[ContextTechnologyOption] match {
                case Some(list) => list.mkString(",")
                case None       => "http"
              }
            state.elementByDef(context) match {
              case container: com.structurizr.model.Container =>
                val name = state.makeDefPath(h, parents).mkString(".")
                val typ = "Handler"
                container.addComponent(name, typ, h.briefValue, technology)
              case _ => // ?
            }
            state
          case _ => state // nothing to do
        }
      case e: Entity =>
        require(parents.length >= 2)
        val name = state.makeDefPath(e, parents).mkString(".")
        val typ: String = {
          val buff = new mutable.StringBuilder()
          if (e.hasOption[EntityTransient]) { buff.append("Transient, ") }
          if (e.hasOption[EntityIsAvailable]) { buff.append("Available, ") }
          if (e.hasOption[EntityIsConsistent]) { buff.append("Consistent, ") }
          if (e.hasOption[EntityEventSourced]) {
            buff.append("Event Sourced, ")
          } else { buff.append("Value, ") }
          if (e.hasOption[EntityIsFiniteStateMachine]) buff
            .append("Finite State Machine, ")
          e.getOptionValue[EntityKind] match {
            case Some(list) => buff.append(list.map(_.s).mkString(", "))
            case None       => buff.append("Entity")
          }
          buff.toString
        }
        val technology: String = {
          e.getOptionValue[EntityTechnologyOption] match {
            case Some(list) => list.map(_.s).mkString(", ")
            case None       => "Arbitrary Technology"
          }
        }
        state.elementByDef(parents.head) match {
          case container: com.structurizr.model.Container =>
            val comp = container
              .addComponent(name, typ, e.briefValue, technology)
            state.elementByDef.addOne((e, comp))
          case _ => // hmm .. bug somewhere
        }
        state
      case s: Story =>
        if (s.userStory.nonEmpty) {
          val actor = s.userStory.get.actor
          val p = state.model
            .addPerson(Location.External, actor.id.value, actor.briefValue)
          state.elementByDef.addOne((actor, p))
          val style = s.getOptionValue[StorySynchronousOption] match {
            case Some(_) => InteractionStyle.Synchronous
            case None    => InteractionStyle.Asynchronous
          }
          val tech = s.getOptionValue[StoryTechnologyOption] match {
            case Some(list) => list.map(_.s).mkString(", ")
            case None       => "JSON/HTTP"
          }
          for {
            aCase <- s.cases
            interaction <- aCase.interactions
          } {
            val domainRef = aCase.scope.get.domainRef
            val elem = pathIdToC4Component(state, domainRef.id)
            val technology = {
              s.getOptionValue[StoryTechnologyOption] match {
                case Some(list) => list.map(_.s).mkString(", ")
                case None       => "http"
              }
            }
            p.uses(elem.asInstanceOf[SoftwareSystem], "requires", technology)
            val from = pathIdToC4Component(state, interaction.from.id)
            val to = pathIdToC4Component(state, interaction.to.id)
            val how = interaction.relationship
            to match {
              case p: Person => from.delivers(p, how, tech, style)
              case s: StaticStructureElement => from.uses(s, how, tech, style)
            }
          }
        }
        state
      case _: Function   => state // functions in contexts only
      case _: Adaptor    => state // TBD
      case _: Processor  => state // TBD
      case _: Projection => state // TBD
      case _: Saga       => state // TBD
      case _: Plant      => state // TBD
      case _: Adaptation => state // TBD
      case _ => // ignore
        state
    }
  }

  def addViews(
    state: C4TranslatorState,
    definition: Definition,
    @unused parents: Seq[Definition]
  ): C4TranslatorState = {
    definition match {
      case rc: RootContainer =>
        // This is the enterprise level
        val enterpriseName = state.options.enterpriseName
          .getOrElse("Unknown Enterprise Name")
        val slv = state.views.createSystemLandscapeView(
          enterpriseName,
          s"Enterprise Landscape of $enterpriseName"
        )
        slv.setEnterpriseBoundaryVisible(true)
        slv.addAllSoftwareSystems()
        slv.addAllPeople()
        state.viewByDef.addOne((rc, slv))
        state
      case d: Domain => state.elementByDef(d) match {
          case ss: SoftwareSystem =>
            val name = state.makeDefPath(d, parents).mkString(".")
            val scv = state.views
              .createSystemContextView(ss, name, d.briefValue)
            scv.setEnterpriseBoundaryVisible(true)
            scv.setTitle(d.identify)
            state.viewByDef.addOne((d, scv))
            state
          case _ => state
        }
      case c: Context =>
        state.elementByDef(c) match {
          case cont: com.structurizr.model.Container =>
            val name = state.makeDefPath(c, parents).mkString(".")
            val cv = state.views.createComponentView(cont, name, c.briefValue)
            cv.addAllComponents()
            state.viewByDef.addOne((c, cv))
        }
        state
      case _: Entity     => state
      case _: Story      => state
      case _: Handler    => state // context handlers only
      case _: Function   => state // functions in contexts only
      case _: Adaptor    => state // TBD
      case _: Processor  => state // TBD
      case _: Projection => state // TBD
      case _: Saga       => state // TBD
      case _: Plant      => state // TBD
      case _: Adaptation => state // TBD
      case _ => // ignore
        state
    }
  }
}
