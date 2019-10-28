package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

object Folding {

  trait State[S <: State[_]] {
    def step(f: S => S): S
  }

  type SimpleDispatch[S] = (Container, Definition, S) => S

  def foldEachDefinition[S](parent: Container, root: Container, state: S)(
    f: SimpleDispatch[S]
  ): S = {
    var result = state
    root match {
      case root: RootContainer =>
        root.containers.foldLeft(result) { (next, container) =>
          foldEachDefinition[S](root, container, next)(f)
        }
      case domain: DomainDef =>
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
      case context: ContextDef =>
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
      case entity: EntityDef =>
        result = f(parent, entity, result)
        result = entity.functions.foldLeft(result) { (next, function) =>
          f(entity, function, next)
        }
        result = entity.invariants.foldLeft(result) { (next, invariant) =>
          f(entity, invariant, next)
        }
        entity.features.foldLeft(result) { (next, feature) =>
          foldEachDefinition[S](entity, feature, next)(f)
        }
      case interaction: InteractionDef =>
        result = f(parent, interaction, result)
        interaction.actions.foldLeft(result) { (next, action) =>
          f(interaction, action, next)
        }
        interaction.roles.foldLeft(result) { (next, role) =>
          f(interaction, role, next)
        }
      case feature: FeatureDef =>
        result = f(parent, feature, result)
        feature.examples.foldLeft(result) { (next, example) =>
          f(feature, example, next)
        }
      case adaptor: AdaptorDef =>
        f(parent, adaptor, result)

      case topic: TopicDef =>
        result = f(parent, topic, result)
        result = topic.commands.foldLeft(result) { (next, command) =>
          f(topic, command, next)
        }
        result = topic.events.foldLeft(result) { (next, event) =>
          f(topic, event, next)
        }
        result = topic.queries.foldLeft(result) { (next, query) =>
          f(topic, query, next)
        }
        result = topic.results.foldLeft(result) { (next, result) =>
          f(topic, result, next)
        }
        result
    }
  }

  trait Folding[S <: State[S]] {

    final def foldLeft(
      root: RootContainer,
      state: S
    ): S = {
      root.containers.foldLeft(state) {
        case (s, content) =>
          foldLeft(root, content, s)
      }
    }

    final def foldLeft(
      parent: Container,
      container: Container,
      initState: S
    ): S = {
      container match {
        case root: RootContainer =>
          foldLeft(root, initState)
        case domain: DomainDef =>
          openDomain(initState, parent, domain)
            .step { state =>
              domain.types.foldLeft(state) { (next, ty) =>
                doType(next, domain, ty)
              }
            }
            .step { state =>
              domain.topics.foldLeft(state) { (next, topic) =>
                foldLeft(domain, topic, next)
              }
            }
            .step { state =>
              domain.contexts.foldLeft(state) { (next, context) =>
                foldLeft(domain, context, next)
              }
            }
            .step { state =>
              domain.interactions.foldLeft(state) { (next, interaction) =>
                foldLeft(domain, interaction, next)
              }
            }
            .step { state =>
              closeDomain(state, parent, domain)
            }

        case context: ContextDef =>
          openContext(initState, parent, context)
            .step { state =>
              context.types.foldLeft(state) { (next, ty) =>
                doType(next, context, ty)
              }
            }
            .step { state =>
              context.adaptors.foldLeft(state) { (next, adaptor) =>
                foldLeft(context, adaptor, next)
              }
            }
            .step { state =>
              context.entities.foldLeft(state) { (next, entity) =>
                foldLeft(context, entity, next)
              }
            }
            .step { state =>
              closeContext(state, parent, context)
            }
        case entity: EntityDef =>
          openEntity(initState, parent, entity)
            .step { state =>
              entity.features.foldLeft(state) { (next, feature) =>
                foldLeft(entity, feature, next)
              }
            }
            .step { state =>
              entity.functions.foldLeft(state) { (next, function) =>
                doFunction(next, entity, function)
              }
            }
            .step { state =>
              entity.invariants.foldLeft(state) { (next, invariant) =>
                doInvariant(next, entity, invariant)
              }
            }
            .step { state =>
              closeEntity(state, parent, entity)
            }
        case interaction: InteractionDef =>
          openInteraction(initState, parent, interaction)
            .step { state =>
              interaction.actions.foldLeft(state) { (next, action) =>
                doAction(next, interaction, action)
              }
            }
            .step { state =>
              interaction.roles.foldLeft(state) { (next, role) =>
                doRole(next, interaction, role)
              }
            }
            .step { state =>
              closeInteraction(state, parent, interaction)
            }
        case feature: FeatureDef =>
          openFeature(initState, parent, feature).step { state =>
            feature.examples.foldLeft(state) { (next, example) =>
              doExample(next, feature, example)
            }
          }
        case adaptor: AdaptorDef =>
          openAdaptor(initState, parent, adaptor).step { state =>
            closeAdaptor(state, parent, adaptor)
          }
        case topic: TopicDef =>
          openTopic(initState, parent, topic)
            .step { state =>
              topic.commands.foldLeft(state) { (next, command) =>
                doCommand(next, topic, command)
              }
            }
            .step { state =>
              topic.events.foldLeft(state) { (next, event) =>
                doEvent(next, topic, event)
              }
            }
            .step { state =>
              topic.queries.foldLeft(state) { (next, query) =>
                doQuery(next, topic, query)
              }
            }
            .step { state =>
              topic.results.foldLeft(state) { (next, result) =>
                doResult(next, topic, result)
              }
            }
            .step { state =>
              closeTopic(state, parent, topic)
            }
      }
    }

    def openDomain(
      state: S,
      container: Container,
      domain: DomainDef
    ): S = { state }

    def closeDomain(
      state: S,
      container: Container,
      domain: DomainDef
    ): S = { state }

    def openContext(
      state: S,
      container: Container,
      context: ContextDef
    ): S = { state }

    def closeContext(
      state: S,
      container: Container,
      context: ContextDef
    ): S = { state }

    def openEntity(
      state: S,
      container: Container,
      entity: EntityDef
    ): S = { state }

    def closeEntity(
      state: S,
      container: Container,
      entity: EntityDef
    ): S = { state }

    def openTopic(
      state: S,
      container: Container,
      channel: TopicDef
    ): S = { state }

    def closeTopic(
      state: S,
      container: Container,
      channel: TopicDef
    ): S = { state }

    def openInteraction(
      state: S,
      container: Container,
      interaction: InteractionDef
    ): S = { state }

    def closeInteraction(
      state: S,
      container: Container,
      interaction: InteractionDef
    ): S = { state }

    def openFeature(
      state: S,
      container: Container,
      feature: FeatureDef
    ): S = { state }

    def closeFeature(
      state: S,
      container: Container,
      feature: FeatureDef
    ): S = { state }

    def openAdaptor(
      state: S,
      container: Container,
      adaptor: AdaptorDef
    ): S = { state }

    def closeAdaptor(
      state: S,
      container: Container,
      adaptor: AdaptorDef
    ): S = { state }

    def doType(
      state: S,
      container: Container,
      typ: TypeDef
    ): S = { state }

    def doCommand(
      state: S,
      container: Container,
      command: CommandDef
    ): S = { state }

    def doEvent(
      state: S,
      container: Container,
      event: EventDef
    ): S = { state }

    def doQuery(
      state: S,
      container: Container,
      query: QueryDef
    ): S = { state }

    def doResult(
      state: S,
      container: Container,
      rslt: ResultDef
    ): S = { state }

    def doAction(
      state: S,
      container: Container,
      action: ActionDef
    ): S = { state }

    def doExample(
      state: S,
      container: Container,
      example: ExampleDef
    ): S = { state }

    def doFunction(
      state: S,
      container: Container,
      function: FunctionDef
    ): S = { state }

    def doInvariant(
      state: S,
      container: Container,
      invariant: InvariantDef
    ): S = { state }

    def doRole(
      state: S,
      container: Container,
      role: RoleDef
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
