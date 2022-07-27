package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path

/** A writer for grpc/protobuffers files */
case class GrpcWriter(
  filePath: Path,
  packages: Seq[String],
  parents: Seq[Parent],
  symTab: SymbolTable
)
    extends TextFileWriter {

  def emitKalixFileHeader: GrpcWriter = {
    sb.append("syntax = \"proto3\";\n\n")
    sb.append(s"package ${packages.mkString(".")};\n\n")

    sb.append("import \"google/api/annotations.proto\";\n")
    sb.append("import \"kalix/annotations.proto\";\n")
    sb.append("import \"validate/validate.proto\";\n")
    sb.append("import \"google/protobuf/empty.proto\";\n\n")
    this
  }

  def reducePathId(pathId: Seq[String]): Seq[String] = {
    pathId // TODO: implement
    // TODO: change RIDDL path ids to package ids
  }

  def reducePathId(pathId: PathIdentifier): Seq[String] = {
    reducePathId(pathId.value)
  }

  def sanitize(s: String): String = {
    s // TODO: remove non-identifier chars?
  }

  def sanitizeId(id: Identifier): String = { sanitize(id.value) }

  def sanitizePathId(pathId: Seq[String]): String = {
    pathId.map(sanitize).mkString(".")
  }
  def sanitizePathId(pathId: PathIdentifier): String = {
    sanitizePathId(pathId.value)
  }

  def emitValidation(tye: TypeExpression): GrpcWriter = {
    tye match {
      case OneOrMore(_, _) => sb
          .append(s"[(validate.rules).repeated = {min_items: 1}];")
      case SpecificRange(_, _, min, max) => sb.append(
          s"[(validate.rules).repeated = {min_items: $min, max_items: $max}];"
        )
      case RangeType(_, min, max) =>
        if (max.n < Int.MaxValue) {
          sb.append(s"[(validate.rules).sint32 = {gte:${min.n}, lt: ${max.n}];")
        } else if (max.n < Long.MaxValue) {
          sb.append(s"[(validate.rules).sint64 = {gte:${min.n}, lt: ${max.n}];")
        }
      // TODO: consider BigInt case validation? Possible?
      case Strng(_, min, max) =>
        val mn = min.map(_.n.toLong).getOrElse(0)
        val mx = max.map(_.n.toLong).getOrElse(Long.MaxValue)
        sb.append(s"[(validate.rules).string = {min_len: $mn, max_len: $mx}];")
      case Pattern(_, regex) => sb
          .append(s"[(validate.rules).string.pattern =\"$regex\"];")
      case _ =>
      // TODO: validate other types
    }
    this
  }

  def emitTypeExpression(tye: TypeExpression): GrpcWriter = {
    tye match {
      case Abstract(_) =>
        sb.append("bytes ") // Structure is unknown, encode as byte string
      case Optional(_, tye2) => emitTypeExpression(tye2)
      case ZeroOrMore(_, tye2) =>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case OneOrMore(_, tye2) =>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case SpecificRange(_, tye2, _, _) =>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case rt: ReferenceType =>
        sb.append(s"string ")
      case mt: TypeRef =>
        sb.append(s"  ${sanitizePathId(mt.id)}")
      case MessageType(_, kind, fields) => sb
          .append("// message type not implemented\n")
      // TODO: implement message type
      case RangeType(_, _, max) =>
        if (max.n <= Int.MaxValue) { sb.append("sint32") }
        else if (max.n <= Long.MaxValue) { sb.append("sint64") }
        else { sb.append("BigInt") }
      case _: Strng     => sb.append("string")
      case _: Number    => sb.append("sint64")
      case _: Bool      => sb.append("bool")
      case _: Integer   => sb.append("sint32")
      case _: Decimal   => sb.append("string")
      case _: Real      => sb.append("double")
      case _: Date      => sb.append("Date")
      case _: DateTime  => sb.append("DateTime")
      case _: Time      => sb.append("Time")
      case _: TimeStamp => sb.append("sint64")
      case _: Duration  => sb.append("Duration")
      case _: LatLong   => sb.append("LatLong")
      case _: URL       => sb.append("string")
      case _: Pattern   => sb.append("string")
      case _: UniqueId  => sb.append("string")
      case _            => ???
    }
    this
  }

  def emitMessageType(typ: Type): GrpcWriter = {
    require(typ.isMessageKind, "Not a message kind")
    val id: Identifier = typ.id
    val ty: MessageType = typ.typ.asInstanceOf[MessageType]
    sb.append(s"message ${sanitizeId(id)} { // ${id.format}\n")
    for {
      (field, n) <- ty.fields.drop(1).zipWithIndex
    } yield {
      field.typeEx match {
        // alternation | referToEntity | mapping | range | typeRef
        case alt: Alternation =>
          sb.append(s"  oneof ${sanitizeId(field.id)} {")
          for { (typ, n) <- alt.of.zipWithIndex } yield {
            emitTypeExpression(typ)
            sb.append(s"f_$n")
          }
        case _ =>
          sb.append("  ")
          emitTypeExpression(field.typeEx)
      }
      sb.append(s" ${sanitizeId(field.id)} = ${n + 1};\n")
    }
    sb.append("}\n\n")
    this
  }

  def emitTypes(types: Seq[Type]): GrpcWriter = {
    this
  }

  def emitEntityTypes(entity: Entity): GrpcWriter = {
    extractMessagePairsFromEntity(entity).flatMap {
      case (ref, maybe_ref) =>
        val indep = referenceToType(ref).map(Seq(_)).getOrElse(Seq.empty)
        val dep = maybe_ref.flatMap(referenceToType(_))
          .map(Seq(_)).getOrElse(Seq.empty)
        indep ++ dep
    }.map { typ => emitMessageType(typ) }
    this
  }

  def emitEntityApi(entity: Entity, packages: Seq[String]): GrpcWriter = {
    if (entity.hasOption[EntityEventSourced]) {
      emitEventSourcedEntityApi(entity, packages)
    } else if (entity.hasOption[EntityValueOption]) {
      emitValueEntityApi(entity)
    } else if (entity.hasOption[EntityTransient]) {
      emitTransientEntityApi(entity)
    } else { emitEventSourcedEntityApi(entity, packages) }
  }

  private def referenceToType(ref: Reference[Type]): Option[Type] = {
    symTab.lookup(ref) match {
      case t :: Nil => Some(t)
      case _ => None
    }
  }

  private def extractMessagePairsFromEntity(
    entity: Entity
  ): Seq[(MessageRef,Option[MessageRef])] = {
    for {
      handler <- entity.handlers
      clause <- handler.clauses
      independentMessage = clause.msg
      example <- clause.examples
      then_ <- example.thens
      action = then_.action
      dependentMessage = action match {
        case PublishAction(_, msg, _, _) => Some(msg.msg)
        case ReplyAction(_, msg, _) => Some(msg.msg)
        case _ => None
      }
    } yield {
      independentMessage -> dependentMessage
    }
  }

  private def emitEventSourcedEntityApi(
    entity: Entity,
    packages: Seq[String]
  ): GrpcWriter = {
    val name = sanitizeId(entity.id)
    val pkgs = packages.mkString(".")
    val fullName = pkgs ++ "." ++ name
    val stateName = fullName + "State"
    val pairs = extractMessagePairsFromEntity(entity)
    val events = pairs.map(_._2).filterNot(_.isEmpty).map(mr =>
      "\"" ++ pkgs ++ "." ++ sanitizePathId(mr.get.id) ++ "\"").mkString(",\n")
    sb.append(
      s"""service ${name}Service {
         |  option (kalix.codegen) = {
         |    event_sourced_entity: {
         |      name: "$fullName"
         |      entity_type: "$name"
         |      state: "$stateName"
         |      events: [
         |        $events
         |      ]
         |    }
         |  };
         |
         |""".stripMargin
    )

    for {
      pair <- pairs
      (indep, dep) = pair
      (inputName, outputName) = indep.messageKind match {
        case CommandKind =>
          val maybeCommandName = referenceToType(indep)
          val maybeEventName = if (dep.nonEmpty) {
            referenceToType(dep.get)
          } else None
          val commandName = if (maybeCommandName.nonEmpty) {
            sanitizeId(maybeCommandName.get.id)
          } else { "Empty" }
          val eventName = if (maybeEventName.nonEmpty) {
            sanitizeId(maybeEventName.get.id)
          } else { "Empty" }
          commandName -> eventName
        case QueryKind =>
          val maybeQueryName = referenceToType(indep)
          val maybeResultName = if (dep.nonEmpty) {
            referenceToType(dep.get)
          } else None
          val queryName = if (maybeQueryName.nonEmpty) {
            sanitizeId(maybeQueryName.get.id)
          } else { "Empty" }
          val resultName = if (maybeResultName.nonEmpty) {
            sanitizeId(maybeResultName.get.id)
          } else { "Empty" }
          queryName -> resultName
        case EventKind =>
          val maybeEventName = referenceToType(indep)
          val eventName = if (maybeEventName.nonEmpty)
            sanitizeId(maybeEventName.get.id)
          else "Empty"
          eventName -> "Empty"
        case _ =>
          require(requirement = false, "Should not be here")
      }
    } yield {
      sb.append(
        s"  rpc establishOrganization ($inputName) returns ($outputName) {}\n"
      )
    }
    sb.append("}\n")
    this
  }

  private def emitValueEntityApi(entity: Entity): GrpcWriter = { this }
  private def emitTransientEntityApi(entity: Entity): GrpcWriter = { this }

  def emitEntityImpl(entity: Entity): GrpcWriter = { this }
}
