package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

/** Unit Tests For Folding */
object Folding {

  def foldLeft[T](
    parent: ContainingDefinition,
    root: ContainingDefinition,
    init: T
  )(
    f: (ContainingDefinition, Definition, T) => T
  ): T = {
    var result = init
    root match {
      case root: RootContainer =>
        result = root.content.foldLeft(result) { (next, container) =>
          foldLeft[T](root, container, next)(f)
        }
      case domain: DomainDef =>
        result = f(parent, domain, result)
        result = domain.types.foldLeft(result) { (next, ty) =>
          f(domain, ty, next)
        }
        result = domain.channels.foldLeft(result) { (next, channel) =>
          f(domain, channel, next)
        }
        result = domain.contexts.foldLeft(result) { (next, context) =>
          foldLeft[T](domain, context, next)(f)
        }
        result = domain.interactions.foldLeft(result) { (next, interaction) =>
          foldLeft[T](domain, interaction, next)(f)
        }
      case context: ContextDef =>
        result = f(parent, context, result)
        result = context.types.foldLeft(result) { (next, ty) =>
          f(context, ty, next)
        }
        result = context.commands.foldLeft(result) { (next, command) =>
          f(context, command, next)
        }
        result = context.events.foldLeft(result) { (next, event) =>
          f(context, event, next)
        }
        result = context.queries.foldLeft(result) { (next, query) =>
          f(context, query, next)
        }
        result = context.results.foldLeft(result) { (next, result) =>
          f(context, result, next)
        }
        result = context.channels.foldLeft(result) { (next, channel) =>
          f(context, channel, next)
        }
        result = context.adaptors.foldLeft(result) { (next, adaptor) =>
          foldLeft[T](context, adaptor, next)(f)
        }
        result = context.entities.foldLeft(result) { (next, entity) =>
          foldLeft[T](context, entity, next)(f)
        }
        result = context.interactions.foldLeft(result) { (next, interaction) =>
          foldLeft[T](context, interaction, next)(f)
        }
      case entity: EntityDef =>
        result = f(parent, entity, result)
        result = entity.features.foldLeft(result) { (next, feature) =>
          foldLeft[T](entity, feature, next)(f)
        }
        result = entity.functions.foldLeft(result) { (next, function) =>
          f(entity, function, next)
        }
        result = entity.invariants.foldLeft(result) { (next, invariant) =>
          f(entity, invariant, next)
        }
      case interaction: InteractionDef =>
        result = f(parent, interaction, result)
        interaction.actions.foldLeft(result) { (next, action) =>
          f(interaction, action, next)
        }
        result = interaction.roles.foldLeft(result) { (next, role) =>
          f(interaction, role, next)
        }
      case feature: FeatureDef =>
        result = f(parent, feature, result)
        result = feature.examples.foldLeft(result) { (next, example) =>
          f(feature, example, next)
        }
      case adaptor: AdaptorDef =>
        result = f(parent, adaptor, result)
    }
    result
  }
}
