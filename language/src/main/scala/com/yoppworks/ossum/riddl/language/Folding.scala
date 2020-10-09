package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

object Folding {

  trait State[S <: State[_]] {
    def step(f: S => S): S
  }

  type SimpleDispatch[S] = (Container, Definition, S) => S

  def foldEachDefinition[S](
    parent: Container,
    container: Container,
    state: S
  )(f: SimpleDispatch[S]
  ): S = {
    var result = state
    container match {
      case root: RootContainer => root.contents
          .foldLeft(result) { (next, container) =>
            foldEachDefinition[S](root, container, next)(f)
          }
      case domain: Domain =>
        result = f(parent, domain, result)
        result = domain.types.foldLeft(result) { (next, ty) =>
          f(domain, ty, next)
        }
        result = domain.topics.foldLeft(result) { (next, topic) =>
          foldEachDefinition[S](domain, topic, next)(f)
        }
        result = domain.contexts.foldLeft(result) { (next, context) =>
          foldEachDefinition[S](domain, context, next)(f)
        }
        domain.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](domain, interaction, next)(f)
        }
      case context: Context =>
        result = f(parent, context, result)
        result = context.types.foldLeft(result) { (next, ty) =>
          f(context, ty, next)
        }
        result = context.adaptors.foldLeft(result) { (next, adaptor) =>
          foldEachDefinition[S](context, adaptor, next)(f)
        }
        result = context.entities.foldLeft(result) { (next, entity) =>
          foldEachDefinition[S](context, entity, next)(f)
        }
        context.interactions.foldLeft(result) { (next, interaction) =>
          foldEachDefinition[S](context, interaction, next)(f)
        }
      case entity: Entity =>
        result = f(parent, entity, result)
        val reducables =
          (entity.types.iterator ++ entity.consumers ++ entity.functions ++
            entity.invariants).toList
        result = reducables.foldLeft(result) { (next, r) => f(entity, r, next) }
        val foldables = (entity.features.iterator ++ entity.states).toList
        foldables.foldLeft(result) { (next, foldable) =>
          foldEachDefinition[S](entity, foldable, next)(f)
        }
      case interaction: Interaction =>
        result = f(parent, interaction, result)
        interaction.actions.foldLeft(result) { (next, action) =>
          f(interaction, action, next)
        }
      case feature: Feature =>
        result = f(parent, feature, result)
        feature.examples.foldLeft(result) { (next, example) =>
          f(feature, example, next)
        }
      case adaptor: Adaptor => f(parent, adaptor, result)
      case topic: Topic =>
        result = f(parent, topic, result)
        val foldables: List[MessageDefinition] =
          (topic.commands.iterator ++ topic.events ++ topic.queries ++
            topic.results).toList
        foldables.foldLeft(result) { (next, message) =>
          foldEachDefinition(topic, message, next)(f)
        }
      case message: MessageDefinition =>
        result = f(parent, message, result)
        message.contents.foldLeft(result) { (next, field) =>
          f(message, field, next)
        }
      case st: AST.State =>
        result = f(parent, st, result)
        st.typeEx match {
          case agg: Aggregation => agg.fields
              .foldLeft(result) { (next, field) => f(st, field, next) }
          case _ => state
        }
    }
  }

  trait Folding[S <: State[S]] {

    //noinspection ScalaStyle
    final def foldLeft(
      parent: Container,
      container: Container,
      initState: S
    ): S = {
      container match {
        case root: RootContainer => root.contents
            .foldLeft(initState) { case (s, content) =>
              foldLeft(root, content, s)
            }
        case domain: Domain => openDomain(initState, parent, domain)
            .step { state =>
              domain.types.foldLeft(state) { (next, ty) =>
                doType(next, domain, ty)
              }
            }.step { state =>
              domain.topics.foldLeft(state) { (next, topic) =>
                foldLeft(domain, topic, next)
              }
            }.step { state =>
              domain.contexts.foldLeft(state) { (next, context) =>
                foldLeft(domain, context, next)
              }
            }.step { state =>
              domain.interactions.foldLeft(state) { (next, interaction) =>
                foldLeft(domain, interaction, next)
              }
            }.step { state => closeDomain(state, parent, domain) }

        case context: Context => openContext(initState, parent, context)
            .step { state =>
              context.types.foldLeft(state) { (next, ty) =>
                doType(next, context, ty)
              }
            }.step { state =>
              context.adaptors.foldLeft(state) { (next, adaptor) =>
                foldLeft(context, adaptor, next)
              }
            }.step { state =>
              context.entities.foldLeft(state) { (next, entity) =>
                foldLeft(context, entity, next)
              }
            }.step { state =>
              context.interactions.foldLeft(state) { (next, in) =>
                foldLeft(context, in, next)
              }
            }.step { state => closeContext(state, parent, context) }
        case entity: Entity => openEntity(initState, parent, entity)
            .step { state =>
              entity.types.foldLeft(state) { (next, typ) =>
                doType(next, entity, typ)
              }
            }.step { state =>
              entity.consumers.foldLeft(state) { (next, consumer) =>
                doConsumer(next, entity, consumer)
              }
            }.step { state =>
              entity.features.foldLeft(state) { (next, feature) =>
                foldLeft(entity, feature, next)
              }
            }.step { state =>
              entity.functions.foldLeft(state) { (next, function) =>
                doFunction(next, entity, function)
              }
            }.step { state =>
              entity.invariants.foldLeft(state) { (next, invariant) =>
                doInvariant(next, entity, invariant)
              }
            }.step { state =>
              entity.states.foldLeft(state) { (next, s) =>
                foldLeft(entity, s, next)
              }
            }.step { state => closeEntity(state, parent, entity) }
        case interaction: Interaction =>
          openInteraction(initState, parent, interaction).step { state =>
            interaction.actions.foldLeft(state) { (next, action) =>
              doAction(next, interaction, action)
            }
          }.step { state => closeInteraction(state, parent, interaction) }
        case feature: Feature => openFeature(initState, parent, feature)
            .step { state =>
              feature.examples.foldLeft(state) { (next, example) =>
                doExample(next, feature, example)
              }
            }
        case adaptor: Adaptor => openAdaptor(initState, parent, adaptor)
            .step { state => closeAdaptor(state, parent, adaptor) }
        case topic: Topic => openTopic(initState, parent, topic).step { state =>
            val foldables: List[MessageDefinition] =
              (topic.commands.iterator ++ topic.events ++ topic.queries ++
                topic.results).toList
            foldables.foldLeft(state) { (next, message) =>
              foldLeft(topic, message, next)
            }
          }.step { state => closeTopic(state, parent, topic) }
        case message: MessageDefinition =>
          openMessage(initState, parent, message).step { state =>
            message.typ match {
              case a: Aggregation => a.fields.foldLeft(state) { (next, field) =>
                  doField(next, message, field)
                }
              case _ => state
            }
          }.step { state => closeMessage(state, parent, message) }
        case st: AST.State => openState(initState, parent, st).step { state =>
            st.typeEx match {
              case agg: Aggregation => agg.fields
                  .foldLeft(state) { (next, field) => doField(next, st, field) }
              case _ => state
            }
          }
      }
    }

    def openDomain(
      state: S,
      container: Container,
      domain: Domain
    ): S = { state }

    def closeDomain(
      state: S,
      container: Container,
      domain: Domain
    ): S = { state }

    def openContext(
      state: S,
      container: Container,
      context: Context
    ): S = { state }

    def closeContext(
      state: S,
      container: Container,
      context: Context
    ): S = { state }

    def openEntity(
      state: S,
      container: Container,
      entity: Entity
    ): S = { state }

    def closeEntity(
      state: S,
      container: Container,
      entity: Entity
    ): S = { state }

    def openState(
      state: S,
      container: Container,
      s: AST.State
    ): S = { state }

    def closeState(
      state: S,
      container: Container,
      s: AST.State
    ): S = { state }

    def openTopic(
      state: S,
      container: Container,
      topic: Topic
    ): S = { state }

    def closeTopic(
      state: S,
      container: Container,
      channel: Topic
    ): S = { state }

    def openInteraction(
      state: S,
      container: Container,
      interaction: Interaction
    ): S = { state }

    def closeInteraction(
      state: S,
      container: Container,
      interaction: Interaction
    ): S = { state }

    def openFeature(
      state: S,
      container: Container,
      feature: Feature
    ): S = { state }

    def closeFeature(
      state: S,
      container: Container,
      feature: Feature
    ): S = { state }

    def openAdaptor(
      state: S,
      container: Container,
      adaptor: Adaptor
    ): S = { state }

    def closeAdaptor(
      state: S,
      container: Container,
      adaptor: Adaptor
    ): S = { state }

    def doType(
      state: S,
      container: Container,
      typ: Type
    ): S = { state }

    def openMessage(
      state: S,
      container: Container,
      message: MessageDefinition
    ): S = {
      message match {
        case e: Event   => openEvent(state, container, e)
        case c: Command => openCommand(state, container, c)
        case q: Query   => openQuery(state, container, q)
        case r: Result  => openResult(state, container, r)
      }
    }

    def openCommand(
      state: S,
      container: Container,
      command: Command
    ): S = { state }

    def openEvent(
      state: S,
      container: Container,
      event: Event
    ): S = { state }

    def openQuery(
      state: S,
      container: Container,
      query: Query
    ): S = { state }

    def openResult(
      state: S,
      container: Container,
      rslt: Result
    ): S = { state }

    def closeMessage(
      state: S,
      container: Container,
      message: MessageDefinition
    ): S = {
      message match {
        case e: Event   => closeEvent(state, container, e)
        case c: Command => closeCommand(state, container, c)
        case q: Query   => closeQuery(state, container, q)
        case r: Result  => closeResult(state, container, r)
      }
    }

    def closeCommand(
      state: S,
      container: Container,
      command: Command
    ): S = { state }

    def closeEvent(
      state: S,
      container: Container,
      event: Event
    ): S = { state }

    def closeQuery(
      state: S,
      container: Container,
      query: Query
    ): S = { state }

    def closeResult(
      state: S,
      container: Container,
      rslt: Result
    ): S = { state }

    def doField(
      state: S,
      container: Container,
      field: Field
    ): S = { state }

    def doConsumer(
      state: S,
      container: Container,
      consumer: Consumer
    ): S = { state }

    def doAction(
      state: S,
      container: Container,
      action: ActionDefinition
    ): S = { state }

    def doExample(
      state: S,
      container: Container,
      example: Example
    ): S = { state }

    def doFunction(
      state: S,
      container: Container,
      function: Function
    ): S = { state }

    def doInvariant(
      state: S,
      container: Container,
      invariant: Invariant
    ): S = { state }

    def doPredefinedType(
      state: S,
      container: Container,
      predef: PredefinedType
    ): S = { state }

    def doTranslationRule(
      state: S,
      container: Container,
      rule: TranslationRule
    ): S = { state }
  }

}
