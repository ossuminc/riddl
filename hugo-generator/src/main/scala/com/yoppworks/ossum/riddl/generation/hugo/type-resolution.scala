package com.yoppworks.ossum.riddl.generation.hugo

import cats.Eval
import cats.instances.all._
import cats.syntax.all._

import scala.annotation.tailrec

object TypeResolution {
  import com.yoppworks.ossum.riddl.generation.hugo.{ RiddlType => RT }
  private type TypePredicate = RiddlType => Boolean

  def apply(root: HugoRoot): HugoRoot = rebuildRoot(Rebuild(TypeResolver(root)), root)

  private case class Rebuild(lookup: TypeResolver) {

    private def rebuildEnumOptions(
      opts: Vector[RT.Enumeration.EnumOption]
    ): Eval[Seq[RT.Enumeration.EnumOption]] = opts.traverse {
      case RT.Enumeration.EnumOptionTyped(name, subtype) => riddl(subtype)
          .map(RT.Enumeration.EnumOptionTyped(name, _))

      case other => Eval.now(other)
    }

    private def rebuildVariantOptions(opts: Vector[RiddlType]): Eval[Seq[RiddlType]] = opts
      .traverse(riddl)

    private def rebuildMapping(from: RiddlType, to: RiddlType): Eval[RT.Mapping] = for {
      fromFixed <- riddl(from)
      toFixed <- riddl(to)
    } yield RT.Mapping(fromFixed, toFixed)

    private def rebuildFields(fields: Set[HugoField]): Eval[Set[HugoField]] = fields
      .unorderedTraverse { field =>
        riddl(field.fieldType).map(HugoField(field.name, _, field.description))
      }

    def riddl(riddlType: RiddlType): Eval[RiddlType] = riddlType match {
      case RT.Alias(inner)             => Eval.defer(riddl(inner)).map(RT.Alias)
      case RT.Collection(inner, empty) => Eval.defer(riddl(inner)).map(RT.Collection(_, empty))
      case RT.Optional(inner)          => Eval.defer(riddl(inner)).map(RT.Optional)
      case RT.Enumeration(of) => Eval.defer(rebuildEnumOptions(of.toVector).map(RT.Enumeration(_)))
      case RT.Variant(of)     => Eval.defer(rebuildVariantOptions(of.toVector).map(RT.Variant))
      case RT.Mapping(from, to) => Eval.defer(rebuildMapping(from, to))
      case RT.UniqueId(inner)   => Eval.defer(riddl(inner)).map(RT.UniqueId)
      case RT.Record(fields)    => Eval.defer(rebuildFields(fields)).map(RT.Record)
      case otherType            => Eval.now(lookup.resolve(otherType))
    }

    def rebuildStates(states: Set[HugoEntity.State]): Eval[Set[HugoEntity.State]] = states
      .unorderedTraverse { state =>
        rebuildFields(state.fields).map(HugoEntity.State(state.name, _))
      }

    def rebuildHandlers(handlers: Set[HugoEntity.Handler]): Eval[Set[HugoEntity.Handler]] = handlers
      .unorderedTraverse { handler =>
        import HugoEntity.OnClause
        handler.clauses.toVector.traverse {
          case OnClause.Event(onType)   => riddl(onType).map(OnClause.Event)
          case OnClause.Command(onType) => riddl(onType).map(OnClause.Command)
          case OnClause.Query(onType)   => riddl(onType).map(OnClause.Query)
          case OnClause.Action(onType)  => riddl(onType).map(OnClause.Action)
        } map { clauses => HugoEntity.Handler(handler.name, clauses) }
      }

    def rebuildFunctions(
      functions: Set[HugoEntity.Function]
    ): Eval[Set[HugoEntity.Function]] = functions.unorderedTraverse { function =>
      for {
        inputs <- rebuildFields(function.inputs)
        output <- riddl(function.output)
      } yield HugoEntity.Function(function.fullName, inputs, output)
    }

  }

  private def rebuildType(ctx: Rebuild, tpe: HugoType, parent: HugoNode): Eval[HugoType] = ctx
    .riddl(tpe.typeDef).map(HugoType(tpe.name, parent, _, tpe.description))

  private def rebuildEntity(ctx: Rebuild, entity: HugoEntity, parent: HugoNode): Eval[HugoEntity] =
    Eval.defer {
      val entityData = for {
        states <- ctx.rebuildStates(entity.states)
        handlers <- ctx.rebuildHandlers(entity.handlers)
        functions <- ctx.rebuildFunctions(entity.functions)
      } yield (states, handlers, functions)

      entityData.map { case (sts, hds, fns) =>
        entity.copy(parent = parent, states = sts, handlers = hds, functions = fns) { self =>
          entity.types.toVector.traverse(rebuildType(ctx, _, self)).value
        }
      }
    }

  private def rebuildContext(
    ctx: Rebuild,
    context: HugoContext,
    parent: HugoNode
  ): Eval[HugoContext] = Eval.always {
    HugoContext(context.name, parent, context.description) { self =>
      val lazyData = for {
        contexts <- context.entities.toVector.traverse(rebuildEntity(ctx, _, self))
        types <- context.types.toVector.traverse(rebuildType(ctx, _, self))
      } yield (contexts, types)

      val (contexts, types) = lazyData.value
      contexts ++ types
    }
  }

  private def rebuildDomain(ctx: Rebuild, domain: HugoDomain, parent: HugoNode): Eval[HugoDomain] =
    Eval.always {
      HugoDomain(domain.name, parent, domain.description) { self =>
        val lazyData = for {
          contexts <- domain.contexts.toVector.traverse(rebuildContext(ctx, _, self))
          domains <- domain.domains.toVector.traverse(rebuildDomain(ctx, _, self))
          types <- domain.types.toVector.traverse(rebuildType(ctx, _, self))
        } yield (contexts, domains, types)

        val (contexts, domains, types) = lazyData.value

        domains ++ types ++ contexts
      }
    }

  private def rebuildRoot(ctx: Rebuild, root: HugoRoot): HugoRoot = HugoRoot { self =>
    root.contents.toVector.collect { case dom: HugoDomain => rebuildDomain(ctx, dom, self).value }
  }

}

trait TypeResolver {
  def resolve(ref: RiddlType): RiddlType
  def resolveAll(refs: Iterable[RiddlType]): Iterable[RiddlType] = refs.map(resolve)
}

object TypeResolver {
  type Unresolved = RiddlType with MustResolve

  def apply(root: HugoRoot): TypeResolver = TypeResolverImpl(root, makeTypeRoot(root))
  def unresolved(root: HugoRoot): Set[Unresolved] = findUnresolved(root)

  private final case class TypeResolverImpl(root: HugoRoot, lookup: Map[String, RiddlType])
      extends TypeResolver {
    @tailrec
    private def search(ns: HugoNode, name: String, orElse: RiddlType): RiddlType =
      lookup.get(ns.resolveName(name)) match {
        case Some(value)        => value
        case None if !ns.isRoot => search(ns.parent, name, orElse)
        case None               => orElse
      }

    private def goSearch(namespace: String, fullName: String, orElse: RiddlType) =
      root.get(namespace) match {
        case Some(node) => search(node, fullName, orElse)
        case None       => orElse
      }

    def resolve(ref: RiddlType): RiddlType = ref match {
      case RiddlType.TypeReference(ns, fullName)   => goSearch(ns, fullName, ref)
      case RiddlType.EntityReference(ns, fullName) => goSearch(ns, fullName, ref)
      case otherRiddlType                          => otherRiddlType
    }
  }

  private def makeTypeRoot(root: HugoRoot): Map[String, RiddlType] = {
    @inline
    def goType(ht: HugoType): (String, RiddlType) = (ht.fullName, RiddlType.TypeRef(ht))

    @inline
    def goEntity(entity: HugoEntity): (String, RiddlType) =
      (entity.fullName, RiddlType.EntityRef(entity))

    def loopTailRec(
      toSearch: Seq[HugoNode],
      found: Map[String, RiddlType]
    ): Eval[Map[String, RiddlType]] = toSearch match {
      case (root: HugoRoot) +: tail   => Eval.defer(loopTailRec(tail ++ root.contents, found))
      case (dom: HugoDomain) +: tail  => Eval.defer(loopTailRec(tail ++ dom.contents, found))
      case (ctx: HugoContext) +: tail => Eval.defer(loopTailRec(tail ++ ctx.contents, found))
      case (ent: HugoEntity) +: tail => Eval
          .defer(loopTailRec(tail ++ ent.contents, found + goEntity(ent)))
      case (tpe: HugoType) +: tail => Eval.defer(loopTailRec(tail, found + goType(tpe)))

      case _ +: tail => Eval.defer(loopTailRec(tail, found))
      case Nil       => Eval.now(found)
    }

    val found = loopTailRec(Seq(root), Map.empty).value
    found
  }

  private def findUnresolved(root: HugoRoot): Set[Unresolved] = {
    def checkEnumOpts(opts: Seq[RiddlType.Enumeration.EnumOption]): Set[Unresolved] =
      goRiddlType(opts.collect { case RiddlType.Enumeration.EnumOptionTyped(_, sub) => sub })

    @tailrec
    def goRiddlType(
      toSearch: Seq[RiddlType],
      found: Set[Unresolved] = Set.empty
    ): Set[Unresolved] = toSearch match {
      case (ref: RiddlType.TypeReference) +: tail   => goRiddlType(tail, found + ref)
      case (ref: RiddlType.EntityReference) +: tail => goRiddlType(tail, found + ref)
      case RiddlType.Alias(inner) +: tail           => goRiddlType(tail :+ inner, found)
      case RiddlType.Optional(inner) +: tail        => goRiddlType(tail :+ inner, found)
      case RiddlType.Collection(inner, _) +: tail   => goRiddlType(tail :+ inner, found)
      case RiddlType.Enumeration(opts) +: tail => goRiddlType(tail, found ++ checkEnumOpts(opts))
      case RiddlType.Variant(opts) +: tail     => goRiddlType(tail ++ opts, found)
      case RiddlType.Mapping(from, to) +: tail => goRiddlType(tail :+ from :+ to, found)
      case RiddlType.UniqueId(inner) +: tail   => goRiddlType(tail :+ inner, found)
      case RiddlType.Record(fields) +: tail => goRiddlType(tail ++ fields.map(_.fieldType), found)
      case _ +: tail                        => goRiddlType(tail, found)
      case Nil                              => found
    }

    def search(toSearch: Seq[HugoNode], found: Set[Unresolved]): Eval[Set[Unresolved]] =
      toSearch match {
        case (root: HugoRoot) +: tail   => Eval.defer(search(tail ++ root.contents, found))
        case (dom: HugoDomain) +: tail  => Eval.defer(search(tail ++ dom.contents, found))
        case (ctx: HugoContext) +: tail => Eval.defer(search(tail ++ ctx.contents, found))
        case (ent: HugoEntity) +: tail  => Eval.defer(search(tail ++ ent.contents, found))
        case (tpe: HugoType) +: tail => Eval
            .defer(search(tail, found ++ goRiddlType(Seq(tpe.typeDef))))
        case _ +: tail => Eval.defer(search(tail, found))
        case Nil       => Eval.now(found)
      }

    search(Seq(root), Set.empty).value
  }

}
