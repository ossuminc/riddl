package com.yoppworks.ossum.riddl.generation.hugo

import com.yoppworks.ossum.riddl.language.Riddl.SysLogger

import scala.annotation.tailrec
import scala.util.control.TailCalls._

object TypeCollector {

  def apply(root: HugoRoot): Namespace = makeTypeRoot(root)

  def typeReferences(root: HugoRoot): Set[HugoType.TypeReference] = makeTypeSet(root)
    .collect { case tr: HugoType.TypeReference => tr }

  @inline
  private final def enumOptOf(of: Seq[HugoType.Enumeration.EnumOption]): Set[HugoType] = of
    .collect { case HugoType.Enumeration.EnumOptionTyped(_, tpe) => tpe }.toSet

  private def makeTypeSet(root: HugoRoot): Set[HugoType] = {
    @inline
    def handleHugoType(
      tpe: HugoType,
      toSearch: Seq[HugoRepr],
      found: Set[HugoType]
    ): TailRec[Set[HugoType]] = tpe match {
      case HugoType.Record(_, fields, _) =>
        tailcall(loopTailRec(toSearch ++ fields.map(_.fieldType), found + tpe))
      case HugoType.Variant(_, of, _) => tailcall(loopTailRec(toSearch ++ of, found + tpe))
      case HugoType.Mapping(_, from, to, _) =>
        tailcall(loopTailRec(toSearch :+ from :+ to, found + tpe))
      case HugoType.Enumeration(_, of, _) =>
        tailcall(loopTailRec(toSearch ++ enumOptOf(of), found + tpe))
      case otherType => tailcall(loopTailRec(toSearch, found + otherType))
    }

    def loopTailRec(toSearch: Seq[HugoRepr], found: Set[HugoType]): TailRec[Set[HugoType]] =
      toSearch match {
        case HugoRoot(root) +: tail        => tailcall(loopTailRec(tail ++ root.hugoNodes, found))
        case HugoDomain(_, ns, _) +: tail  => tailcall(loopTailRec(tail ++ ns.hugoNodes, found))
        case HugoContext(_, ns, _) +: tail => tailcall(loopTailRec(tail ++ ns.hugoNodes, found))

        case HugoEntity(_, ns, _, _, _, _, _, _) +: tail =>
          tailcall(loopTailRec(tail ++ ns.hugoNodes, found))

        case (tpe: HugoType) +: tail => handleHugoType(tpe, tail, found)

        case _ +: tail => tailcall(loopTailRec(tail, found))
        case Nil       => done(found)
      }

    val found = loopTailRec(Seq(root), Set.empty).result
    found
  }

  private def makeTypeRoot(root: HugoRoot): Namespace = {
    def searchRec(nodes: Seq[HugoRepr], builder: Namespace): TailRec[Namespace] = {
      @inline
      def goRec(node: RiddlContainer, tail: Seq[HugoRepr], ns: Namespace) = {
        val childNs = ns.getOrCreate(node.name).setNode(node)
        searchRec(node.contents.toSeq, childNs).flatMap { _ => searchRec(tail, ns) }
      }

      @inline
      def handleEntity(entity: HugoEntity, tail: Seq[HugoRepr], ns: Namespace) = {
        val entityNs = ns.getOrCreate(entity.name).setNode(entity)
        searchRec(entity.namespace.hugoNodes.toSeq, entityNs).flatMap(_ => searchRec(tail, ns))
      }

      def handleType(tpe: HugoType, tail: Seq[HugoRepr], ns: Namespace) = {
        @inline
        def addAndSearch(ht: HugoType, toSearch: Iterable[HugoType]) = {
          ns.getOrCreate(ht.name).setNode(ht)
          searchRec(tail ++ toSearch, ns)
        }

        tpe match {
          case HugoType.Record(_, fields, _)    => addAndSearch(tpe, fields.map(_.fieldType))
          case HugoType.Variant(_, of, _)       => addAndSearch(tpe, of)
          case HugoType.Mapping(_, from, to, _) => addAndSearch(tpe, from :: to :: Nil)
          case HugoType.Enumeration(_, of, _)   => addAndSearch(tpe, enumOptOf(of))
          case _                                => addAndSearch(tpe, Seq.empty)
        }
      }

      nodes match {
        case (dom: HugoDomain) +: tail  => tailcall(goRec(dom, tail, builder))
        case (ctx: HugoContext) +: tail => tailcall(goRec(ctx, tail, builder))
        case (ent: HugoEntity) +: tail  => tailcall(handleEntity(ent, tail, builder))
        // Skip type references
        case (_: HugoType.TypeReference) +: tail => tailcall(searchRec(tail, builder))
        case (tpe: HugoType) +: tail             => tailcall(handleType(tpe, tail, builder))
        case Nil                                 => done(builder)
      }
    }
    searchRec(root.contents.toSeq, Namespace.emptyRoot).result
  }

}

sealed trait TypeResolver {
  def resolve(ref: HugoType.TypeReference): HugoRepr
  def resolveAll(refs: Iterable[HugoType.TypeReference]): Iterable[HugoRepr] = refs.map(resolve)
}

object TypeResolver {

  def apply(root: HugoRoot): TypeResolver = TypeResolverImpl(TypeCollector(root))

  private final case class TypeResolverImpl(types: Namespace) extends TypeResolver {
    def resolve(ref: HugoType.TypeReference): HugoRepr = {
      @tailrec
      def search(nn: Namespace, toFind: Seq[String], found: Set[HugoRepr]): Set[HugoRepr] =
        if (nn.isRoot) { found }
        else if (toFind.length == 1) {
          val foundNode = nn.get(toFind.last).flatMap(_.node)
          search(nn.parent, toFind, found ++ foundNode)
        } else {
          val foundAtNn = toFind.foldLeft(Option(nn)) { case (ns, part) =>
            ns.fold(Option.empty[Namespace])(_.get(part))
          }
          val foundHugo = foundAtNn match {
            case Some(Namespace(Some(hugo), _, _)) => Some(hugo)
            case _                                 => None
          }
          search(nn.parent, toFind, found ++ foundHugo)
        }

      val searchNamespace = types.getOrCreate(ref.namespace.fullName)
      val searchName = ref.fullName.split('.')
      val found = search(searchNamespace, searchName, Set.empty).toList
      found match {
        case Nil        => ref
        case one :: Nil => one
        case many       => SysLogger.warn(s"$ref resolved to more than one type"); ref
      }
    }

  }

}
