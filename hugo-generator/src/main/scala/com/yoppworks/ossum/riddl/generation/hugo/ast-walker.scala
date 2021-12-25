package com.yoppworks.ossum.riddl.generation.hugo

import com.yoppworks.ossum.riddl.language.AST

sealed trait WalkNodeF[In, Out <: HugoNode] {
  def apply(ns: HugoNode, inputNode: In): Out
}

private object WalkNodeF {
  final def apply[In, Out <: HugoNode](f: (HugoNode, In) => Out): WalkNodeF[In, Out] =
    new WalkNodeF[In, Out] {
      final def apply(ns: HugoNode, inputNode: In) = f(ns, inputNode)
    }
}

object LukeAstWalker {

  def apply(
    root: AST.RootContainer
  ): HugoRoot = HugoRoot { ns =>
    root.contents.collect {
      case (dom: AST.Domain)  => walkDomain(ns, dom)
      case (ctx: AST.Context) => walkContext(ns, ctx)
      case (ent: AST.Entity)  => walkEntity(ns, ent)
    }
  }

  val walkDomain: WalkNodeF[AST.Domain, HugoDomain] = WalkNodeF { (ns, node) =>
    HugoDomain(node.id.value, ns) { domain =>
      node.types.map(walkType(domain, _)) ++ node.contexts.map(walkContext(domain, _)) ++
        node.domains.map(walkDomain(domain, _))
    }
  }

  val walkContext: WalkNodeF[AST.Context, HugoContext] = WalkNodeF { (ns, node) =>
    HugoContext(node.id.value, ns) { context =>
      node.types.map(walkType(context, _)) ++ node.entities.map(walkEntity(context, _))
    }
  }

  val walkEntity: WalkNodeF[AST.Entity, HugoEntity] = WalkNodeF { (ns, node) =>
    val name = node.id.value
    val entityName = ns.resolveName(name)
    def resolveName(n: String) = entityName + s".$n"

    val options: HugoEntity.Options = node.options
      .foldLeft(HugoEntity.EntityOption.none) { case (opts, entityOpt) =>
        entityOpt match {
          case AST.EntityEventSourced(_)       => opts + HugoEntity.EntityOption.EventSourced
          case AST.EntityValueOption(_)        => opts + HugoEntity.EntityOption.ValueType
          case AST.EntityAggregate(_)          => opts + HugoEntity.EntityOption.Aggregate
          case AST.EntityPersistent(_)         => opts + HugoEntity.EntityOption.Persistent
          case AST.EntityConsistent(_)         => opts + HugoEntity.EntityOption.Consistent
          case AST.EntityAvailable(_)          => opts + HugoEntity.EntityOption.Available
          case AST.EntityFiniteStateMachine(_) => opts + HugoEntity.EntityOption.FiniteStateMachine
        }
      }

    val states = node.states.map { astState =>
      HugoEntity.State(
        resolveName(astState.id.value),
        astState.contents.collect { case AST.Field(_, id, typeEx, description) =>
          HugoField(
            id.value,
            handleTypeExpr(entityName, typeEx),
            description.map(descriptionToHugo)
          )
        }.distinct.toSet
      )
    }

    @inline
    def mkTypeRef(ns: String, path: AST.PathIdentifier) = RiddlType.TypeReference(ns, path.value)

    @inline
    def handlerOnClause(nn: String, hc: AST.OnClause): HugoEntity.OnClause = hc.msg match {
      case AST.CommandRef(_, id) => HugoEntity.OnClause.Command(mkTypeRef(nn, id))
      case AST.EventRef(_, id)   => HugoEntity.OnClause.Event(mkTypeRef(nn, id))
      case AST.QueryRef(_, id)   => HugoEntity.OnClause.Query(mkTypeRef(nn, id))
      case AST.ResultRef(_, id)  => HugoEntity.OnClause.Action(mkTypeRef(nn, id))
    }

    val handlers = node.handlers.map { astHandler =>
      val handlerName = resolveName(astHandler.id.value)
      val clauses = astHandler.clauses.map(handlerOnClause(entityName, _))
      HugoEntity.Handler(handlerName, clauses)
    }

    val functions = node.functions.map { astFunction =>
      val funcName = resolveName(astFunction.id.value)
      val inputs = astFunction.input.collect { case AST.Aggregation(_, fields, _) =>
        fields.map { field => HugoField(field.id.value, handleTypeExpr(entityName, field.typeEx)) }
          .distinct.toSet
      }.fold(Set.empty[HugoField])(identity)
      val output = handleTypeExpr(entityName, astFunction.output)
      HugoEntity.Function(funcName, inputs, output)
    }

    val invariants = node.invariants.map { astInvariant =>
      HugoEntity
        .Invariant(resolveName(astInvariant.id.value), astInvariant.expression.map(_.s).toList)
    }

    HugoEntity(
      name,
      ns,
      options,
      states.toSet,
      handlers.toSet,
      functions.toSet,
      invariants.toSet,
      node.description.map(descriptionToHugo)
    )(parent => node.types.map(walkType(parent, _)))
  }

  val walkType: WalkNodeF[AST.Type, HugoType] = WalkNodeF { (ns, node) =>
    val name = node.id.value
    val typeDef = handleTypeExpr(ns.fullName, node.typ) match {
      case tpe: RiddlType.PredefinedType => RiddlType.Alias(tpe)
      case otherwise                     => otherwise
    }
    HugoType(name, ns, typeDef, node.description.map(descriptionToHugo))
  }

  private def handleTypeExpr(ns: String, expr: AST.TypeExpression): RiddlType = expr match {
    case AST.Optional(_, texp) => RiddlType.Optional(handleTypeExpr(ns, texp))

    case AST.ZeroOrMore(_, texp) => RiddlType
        .Collection(handleTypeExpr(ns, texp), canBeEmpty = true)

    case AST.OneOrMore(_, texp) => RiddlType
        .Collection(handleTypeExpr(ns, texp), canBeEmpty = false)

    case AST.Enumeration(_, of, _) =>
      val opts: Seq[RiddlType.Enumeration.EnumOption] = of.map {
        case AST.Enumerator(_, id, Some(value), None, _) => RiddlType.Enumeration
            .EnumOptionValue(id.value, value.n.toInt)

        case AST.Enumerator(_, id, None, Some(ref), _) => RiddlType.Enumeration
            .EnumOptionTyped(id.value, RiddlType.TypeReference(ns, ref.id.value))

        case AST.Enumerator(_, id, _, _, _) => RiddlType.Enumeration.EnumOptionNamed(id.value)
      }
      RiddlType.Enumeration(opts)

    case AST.Alternation(_, of, _) =>
      val variants = of.map(handleTypeExpr(ns, _))
      RiddlType.Variant(variants)

    case AST.Aggregation(_, fields, _) =>
      val recFields = fields.map(fld =>
        HugoField(
          fld.id.value,
          handleTypeExpr(ns, fld.typeEx),
          fld.description.map(descriptionToHugo)
        )
      )
      RiddlType.Record(recFields.distinct.toSet)

    case AST.Mapping(_, from, to, _) =>
      val fromType = handleTypeExpr(ns, from)
      val toType = handleTypeExpr(ns, to)
      RiddlType.Mapping(fromType, toType)

    case AST.RangeType(_, min, max, _) => RiddlType.Range(min.n.toInt, max.n.toInt)

    case AST.Pattern(_, pattern, _) => RiddlType.RegexPattern(pattern.map(_.s))

    /* A `UniqueId` with no link is just a UUID! */
    case AST.UniqueId(_, e, _) if e.value.isEmpty => RiddlType.PredefinedType.UUID

    case AST.UniqueId(_, entityPath, _) => RiddlType.EntityReference(ns, entityPath.value)

    case AST.TypeRef(_, id) => RiddlType.TypeReference(ns, id.value)

    /* Predefined types */
    case AST.PredefinedType("String")    => RiddlType.PredefinedType.Text
    case AST.PredefinedType("Boolean")   => RiddlType.PredefinedType.Bool
    case AST.PredefinedType("Number")    => RiddlType.PredefinedType.Number
    case AST.PredefinedType("Integer")   => RiddlType.PredefinedType.Integer
    case AST.PredefinedType("Decimal")   => RiddlType.PredefinedType.Decimal
    case AST.PredefinedType("Real")      => RiddlType.PredefinedType.Real
    case AST.PredefinedType("Date")      => RiddlType.PredefinedType.Date
    case AST.PredefinedType("Time")      => RiddlType.PredefinedType.Time
    case AST.PredefinedType("DateTime")  => RiddlType.PredefinedType.DateTime
    case AST.PredefinedType("TimeStamp") => RiddlType.PredefinedType.Timestamp
    case AST.PredefinedType("Duration")  => RiddlType.PredefinedType.Duration
    case AST.PredefinedType("UUID")      => RiddlType.PredefinedType.UUID
    case AST.PredefinedType("URL")       => RiddlType.PredefinedType.URL
    case AST.PredefinedType("LatLong")   => RiddlType.PredefinedType.LatLong
    case AST.PredefinedType("Nothing")   => RiddlType.PredefinedType.Bottom

    /* Default */
    case _ => RiddlType.UnhandledType(expr.toString)
  }

  private final def descriptionToHugo(desc: AST.Description): HugoDescription = HugoDescription(
    desc.brief.map(_.s).mkString(" "),
    desc.details.map(_.s).toList,
    desc.citations.map(_.s).toList
  )

}
