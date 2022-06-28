package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path

/** A writer for grpc/protobuffers files */
case class GrpcWriter(
  filePath: Path,
  root: RootContainer,
  symtab: SymbolTable)
    extends TextFileWriter {

  def emitKalixFileHeader(packages: Seq[String]): GrpcWriter = {
    sb.append("syntax = \"proto3\";\n\n")
    sb.append(s"package ${packages.mkString(".")};\n\n")

    sb.append("import \"google/api/annotations.proto\";\n")
    sb.append("import \"kalix/annotations.proto\";\n")
    this
  }

  def sanitizeId(id: Identifier): String = {
    id.value // FIXME: remove non-identifier chars?
  }

  def emitTypeExpression(tye: TypeExpression): GrpcWriter = {
    tye match {
      case s: Strng => sb.append("string")
      case n: Number => sb.append("sint64")
      case _ => ???
    }
    this
  }

  def emitType(ty: Type): GrpcWriter = {
    sb.append(s"message ${sanitizeId(ty.id)} { // ${ty.format}")
    // TODO: finish implementation
    this
  }
}
