package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path

/** A writer for grpc/protobuffers files */
case class GrpcWriter(
  filePath: Path,
  packages: Seq[String],
  parents: Seq[Parent]
) extends TextFileWriter {

  def emitKalixFileHeader: GrpcWriter = {
    sb.append("syntax = \"proto3\";\n\n")
    sb.append(s"package ${packages.mkString(".")};\n\n")

    sb.append("import \"google/api/annotations.proto\";\n")
    sb.append("import \"kalix/annotations.proto\";\n")
    sb.append("import \"validate/validate.proto\";\n")
    this
  }

  def reducePathId(pathId: PathIdentifier): Seq[String] = {
    val pidNames = pathId.value
    pidNames // TODO: implement
    // TODO: change RIDDL path ids to package ids
  }

  def sanitize(s: String): String = {
    s // TODO: remove non-identifier chars?
  }

  def sanitizeId(id: Identifier): String = {
    sanitize(id.value)
  }

  def sanitizePathId(pathId: PathIdentifier): String = {
    reducePathId(pathId).map(sanitize).mkString(".")
  }

  def emitValidation(tye: TypeExpression): GrpcWriter = {
    tye match {
      case OneOrMore(_, _) =>
        sb.append(s"[(validate.rules).repeated = {min_items: 1}];")
      case SpecificRange(_, _, min, max) =>
        sb.append(
          s"[(validate.rules).repeated = {min_items: $min, max_items: $max}];"
        )
      case RangeType(_, min, max) =>
        if (max.n < Int.MaxValue) {
          sb.append(
            s"[(validate.rules).sint32 = {gte:${min.n}, lt: ${max.n}];"
          )
        }
        else if (max.n < Long.MaxValue) {
          sb.append(
            s"[(validate.rules).sint64 = {gte:${min.n}, lt: ${max.n}];"
          )
        }
        // TODO: consider BigInt case validation? Possible?
      case _ =>
        // TODO: validate other types
    }
    this
  }

  def emitTypeExpression(tye: TypeExpression): GrpcWriter = {
    tye match {
      case Abstract(_) =>
        sb.append("bytes ") // Structure is unknown, encode as byte string
      case _: Bool =>
        sb.append("bool")
      case Optional(_,tye2) =>
        emitTypeExpression(tye2)
      case ZeroOrMore(_,tye2) =>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case OneOrMore(_, tye2) =>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case SpecificRange(_, tye2, _, _)=>
        sb.append("repeated ")
        emitTypeExpression(tye2)
      case MessageType(_,kind, fields) =>

      case _: Date =>
        sb.append("Date")
      case _: DateTime =>
        sb.append("DateTime")
      case RangeType(_,_,max) =>
        if (max.n <= Int.MaxValue) {
          sb.append("sint32")
        } else if (max.n <= Long.MaxValue) {
          sb.append("sint64")
        } else {
          sb.append("BigInt")
        }
      case _: Strng => sb.append("string")
      case _: Number => sb.append("sint64")
      case _ => ???
    }
    this
  }

  def emitMessageType(typ: Type ): GrpcWriter = {
    require(typ.isMessageKind, "Not a message kind")
    val id: Identifier = typ.id
    val ty: MessageType = typ.typ.asInstanceOf[MessageType]
    sb.append(s"message ${sanitizeId(id)} { // ${id.format}\n  ")
    for { (field,n) <- ty.fields.zipWithIndex } {
      field.typeEx match {
        case mt: TypeRef =>
          sb.append(s"  ${sanitizePathId(mt.id)}")

        case _ =>
          emitTypeExpression(field.typeEx)
      }
      sb.append(s" ${sanitizeId(field.id)} = ${n+1}")
    }
    // TODO: finish implementation
    this
  }

  def emitTypes(types: Seq[Type]): GrpcWriter = {
    this
  }

}
