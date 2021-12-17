package com.yoppworks.ossum.riddl.generation.hugo

import com.yoppworks.ossum.riddl.language.AST

sealed trait WalkNodeF[In, Out <: HugoRepr] {
  def apply(nn: Namespace, inputNode: In): Out
}

private object WalkNodeF {
  final def apply[In, Out <: HugoRepr](f: (Namespace, In) => Out): WalkNodeF[In, Out] =
    new WalkNodeF[In, Out] {
      final def apply(nn: Namespace, inputNode: In) = f(nn, inputNode)
    }
}

object LukeAstWalker {

  def apply(
    root: AST.RootContainer
  ): HugoRoot = HugoRoot(root.contents.foldLeft(Namespace.emptyRoot) {
    case (ns, dom: AST.Domain)  => walkDomain(ns, dom); ns
    case (ns, ctx: AST.Context) => walkContext(ns, ctx); ns
    case (ns, ent: AST.Entity)  => walkEntity(ns, ent); ns
    case (ns, _)                => ns
  })

  val walkDomain: WalkNodeF[AST.Domain, HugoDomain] = WalkNodeF { (nn, node) =>
    val name = node.id.value
    val domainNs = nn.getOrCreate(name)
    val types = node.types.map(walkType(domainNs, _))
    val contexts = node.contexts.map(walkContext(domainNs, _))
    val domains = node.domains.map(walkDomain(domainNs, _))
    val dom = HugoDomain(name, domainNs, node.description.map(descriptionToHugo))
    domainNs.setNode(dom)
    dom
  }

  val walkContext: WalkNodeF[AST.Context, HugoContext] = WalkNodeF { (nn, node) =>
    val name = node.id.value
    val ctxNs = nn.getOrCreate(name)
    val types = node.types.map(walkType(ctxNs, _))
    val entities = node.entities.map(walkEntity(ctxNs, _))
    val ctx = HugoContext(name, ctxNs, node.description.map(descriptionToHugo))
    ctxNs.setNode(ctx)
    ctx
  }

  val walkEntity: WalkNodeF[AST.Entity, HugoEntity] = WalkNodeF { (nn, node) =>
    val name = node.id.value
    val entityNs = nn.getOrCreate(name)
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
        entityNs.resolve(astState.id.value),
        astState.contents.collect { case AST.Field(_, id, typeEx, description) =>
          HugoField(id.value, handleTypeExpr(entityNs, typeEx), description.map(descriptionToHugo))
        }.distinct.toSet
      )
    }

    val types = node.types.map(walkType(entityNs, _))

    @inline
    def mkTypeRef(ns: Namespace, path: AST.PathIdentifier) = HugoType.TypeReference(ns, path.value)

    @inline
    def handlerOnClause(nn: Namespace, hc: AST.OnClause): HugoEntity.OnClause = hc.msg match {
      case AST.CommandRef(_, id) => HugoEntity.OnClause.Command(mkTypeRef(nn, id))
      case AST.EventRef(_, id)   => HugoEntity.OnClause.Event(mkTypeRef(nn, id))
      case AST.QueryRef(_, id)   => HugoEntity.OnClause.Query(mkTypeRef(nn, id))
      case AST.ResultRef(_, id)  => HugoEntity.OnClause.Action(mkTypeRef(nn, id))
    }

    val handlers = node.handlers.map { astHandler =>
      val handlerName = entityNs.resolve(astHandler.id.value)
      val clauses = astHandler.clauses.map(handlerOnClause(entityNs, _))
      HugoEntity.Handler(handlerName, clauses)
    }

    val functions = node.functions.map { astFunction =>
      val funcName = entityNs.resolve(astFunction.id.value)
      val inputs = astFunction.input.collect { case AST.Aggregation(_, fields, _) =>
        fields.map { field => HugoField(field.id.value, handleTypeExpr(entityNs, field.typeEx)) }
          .distinct.toSet
      }.fold(Set.empty[HugoField])(identity)
      val output = handleTypeExpr(entityNs, astFunction.output)
      HugoEntity.Function(funcName, inputs, output)
    }

    val invariants = node.invariants.map { astInvariant =>
      HugoEntity
        .Invariant(entityNs.resolve(astInvariant.id.value), astInvariant.expression.map(_.s).toList)
    }

    val entity = HugoEntity(
      entityNs.name,
      entityNs,
      options,
      states.toSet,
      handlers.toSet,
      functions.toSet,
      invariants.toSet,
      node.description.map(descriptionToHugo)
    )

    entityNs.setNode(entity)
    entity
  }

  val walkType: WalkNodeF[AST.Type, HugoType] = WalkNodeF { (nn, node) =>
    val name = node.id.value
    val typeNs = nn.getOrCreate(name)
    val tpe = handleTypeExpr(typeNs, node.typ) match {
      case tpe: HugoType.PredefinedType => HugoType.Alias(typeNs.fullName, tpe)
      case otherwise                    => otherwise
    }
    typeNs.setNode(tpe)
    tpe
  }

  private def handleTypeExpr(nn: Namespace, expr: AST.TypeExpression): HugoType = expr match {
    case AST.Optional(_, texp) => HugoType.Optional(nn.fullName, handleTypeExpr(nn.parent, texp))

    case AST.ZeroOrMore(_, texp) => HugoType
        .Collection(nn.fullName, handleTypeExpr(nn.parent, texp), canBeEmpty = true)

    case AST.OneOrMore(_, texp) => HugoType
        .Collection(nn.fullName, handleTypeExpr(nn.parent, texp), canBeEmpty = false)

    case AST.Enumeration(_, of, description) =>
      val opts: Seq[HugoType.Enumeration.EnumOption] = of.map {
        case AST.Enumerator(_, id, Some(value), None, _) => HugoType.Enumeration
            .EnumOptionValue(id.value, value.n.toInt)

        case AST.Enumerator(_, id, None, Some(ref), _) => HugoType.Enumeration
            .EnumOptionTyped(id.value, HugoType.TypeReference(nn, ref.id.value))

        case AST.Enumerator(_, id, _, _, _) => HugoType.Enumeration.EnumOptionNamed(id.value)
      }
      HugoType.Enumeration(nn.fullName, opts, description.map(descriptionToHugo))

    case AST.Alternation(_, of, description) =>
      val variants = of.map(handleTypeExpr(nn.parent, _))
      HugoType.Variant(nn.fullName, variants, description.map(descriptionToHugo))

    case AST.Aggregation(_, fields, description) =>
      val recFields = fields.map(fld =>
        HugoField(
          fld.id.value,
          handleTypeExpr(nn, fld.typeEx),
          fld.description.map(descriptionToHugo)
        )
      )
      HugoType.Record(nn.fullName, recFields.distinct.toSet, description.map(descriptionToHugo))

    case AST.Mapping(_, from, to, description) =>
      val fromType = handleTypeExpr(nn.parent, from)
      val toType = handleTypeExpr(nn.parent, to)
      HugoType.Mapping(nn.fullName, fromType, toType, description.map(descriptionToHugo))

    case AST.RangeType(_, min, max, description) => HugoType
        .Range(nn.fullName, min.n.toInt, max.n.toInt, description.map(descriptionToHugo))

    case AST.Pattern(_, pattern, description) => HugoType
        .RegexPattern(nn.fullName, pattern.map(_.s), description.map(descriptionToHugo))

    case AST.UniqueId(_, entityPath, description) => HugoType.ReferenceType(
        nn.fullName,
        HugoType.TypeReference(nn.parent, entityPath.value),
        description.map(descriptionToHugo)
      )

    case AST.TypeRef(_, id) => HugoType.TypeReference(nn, id.value.mkString("."))

    /* Predefined types */
    case AST.PredefinedType("String")    => HugoType.PredefinedType.Text
    case AST.PredefinedType("Boolean")   => HugoType.PredefinedType.Bool
    case AST.PredefinedType("Number")    => HugoType.PredefinedType.Number
    case AST.PredefinedType("Integer")   => HugoType.PredefinedType.Integer
    case AST.PredefinedType("Decimal")   => HugoType.PredefinedType.Decimal
    case AST.PredefinedType("Real")      => HugoType.PredefinedType.Real
    case AST.PredefinedType("Date")      => HugoType.PredefinedType.Date
    case AST.PredefinedType("Time")      => HugoType.PredefinedType.Time
    case AST.PredefinedType("DateTime")  => HugoType.PredefinedType.DateTime
    case AST.PredefinedType("TimeStamp") => HugoType.PredefinedType.Timestamp
    case AST.PredefinedType("Duration")  => HugoType.PredefinedType.Duration
    case AST.PredefinedType("UUID")      => HugoType.PredefinedType.UUID
    case AST.PredefinedType("URL")       => HugoType.PredefinedType.URL
    case AST.PredefinedType("LatLong")   => HugoType.PredefinedType.LatLong
    case AST.PredefinedType("Nothing")   => HugoType.PredefinedType.Bottom

    /* Default */
    case _ => HugoType.UnhandledType(nn.fullName)
  }

  private final def descriptionToHugo(desc: AST.Description): HugoDescription = HugoDescription(
    desc.brief.map(_.s).mkString(" "),
    desc.details.map(_.s).toList,
    desc.citations.map(_.s).toList
  )

}
