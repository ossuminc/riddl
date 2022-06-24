package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.utils.TextFileWriter

import java.nio.file.Path

/** A writer for grpc/protobuffers files */
case class GrpcWriter(
  filePath: Path,
  root: RootContainer,
  context: Context,
  symtab: SymbolTable)
    extends TextFileWriter(filePath) {

  def superScopeTypes(): GrpcWriter = {
    val parents = symtab.parentsOf(context)
    for {
      parent <- symtab.parentsOf(context) ++ Seq(context)
    } yield parent match {
      case d: Domain  => for { t <- d.types } yield { emitType(t) }
      case c: Context => for { t <- c.types } yield { emitType(t) }
    }
    this
  }

  def sanitizeId(id: Identifier): String = {
    id.value // FIXME: remove non-identifier chars?
  }

  def emitKalixFileHeader: GrpcWriter = {
    sb.append("syntax = \"proto3\";\n\n")
    sb.append("package com.improving.app.gateway.api;\n\n")

    sb.append("import \"google/api/annotations.proto\";\n")
    sb.append("import \"kalix/annotations.proto\";\n")
    this
  }

  def emitType(ty: Type): GrpcWriter = {
    sb.append(s"message ${sanitizeId(ty.id)} { // ${ty.format}")
    ty.typ match { case n: Number => sb.append("one of ") }
    // TODO: finish implementation
    this
  }
}
