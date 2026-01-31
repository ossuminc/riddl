/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}

/** Cross-platform BAST tests that run on JVM, JS, and Native.
  *
  * These tests verify BAST serialization and deserialization work correctly
  * across all supported platforms by building AST programmatically (avoiding
  * the parser's BAST import loading which uses blocking I/O).
  */
class SharedBASTTest extends AbstractTestingBasis {

  "BAST Cross-Platform" should {

    "serialize and deserialize a simple domain" in {
      // Build AST programmatically using correct constructors
      val typeExpr = String_(At())
      val typeDef = Type(At(), Identifier(At(), "Foo"), typeExpr)
      val domain = Domain(At(), Identifier(At(), "Simple"), Contents(typeDef))
      val root = Root(At(), Contents(domain))

      // Serialize to BAST
      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
      val bastBytes = output.bytes

      bastBytes.length must be > 0
      output.nodeCount must be > 0

      // Deserialize from BAST
      BASTReader.read(bastBytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
          succeed
        case Left(errors) =>
          fail(s"BAST read failed: ${errors.format}")
      }
    }

    "serialize and deserialize a domain with context and entity" in {
      // Build AST: domain > context > entity
      val handler = Handler(At(), Identifier(At(), "TestHandler"))
      val entity = Entity(At(), Identifier(At(), "TestEntity"), Contents(handler))
      val context = Context(At(), Identifier(At(), "TestContext"), Contents(entity))
      val domain = Domain(At(), Identifier(At(), "TestDomain"), Contents(context))
      val root = Root(At(), Contents(domain))

      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
      val bastBytes = output.bytes

      BASTReader.read(bastBytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
          succeed
        case Left(errs) =>
          fail(s"BAST read failed: ${errs.format}")
      }
    }

    "serialize and deserialize various type expressions" in {
      // Build AST with multiple type expressions
      val types: Seq[Type] = Seq(
        Type(At(), Identifier(At(), "SimpleString"), String_(At())),
        Type(At(), Identifier(At(), "SimpleNumber"), Number(At())),
        Type(At(), Identifier(At(), "SimpleBoolean"), Bool(At())),
        Type(At(), Identifier(At(), "OptionalString"), Optional(At(), String_(At()))),
        Type(At(), Identifier(At(), "ListOfStrings"), OneOrMore(At(), String_(At()))),
        Type(At(), Identifier(At(), "MappingType"), Mapping(At(), String_(At()), Number(At())))
      )
      val domain = Domain(At(), Identifier(At(), "TypesDomain"), Contents(types*))
      val root = Root(At(), Contents(domain))

      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
      val bastBytes = output.bytes

      BASTReader.read(bastBytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
          succeed
        case Left(errs) =>
          fail(s"BAST read failed: ${errs.format}")
      }
    }

    "handle multiple domains" in {
      // Build AST with multiple domains
      val domain1 = Domain(At(), Identifier(At(), "First"), Contents(
        Type(At(), Identifier(At(), "A"), String_(At()))
      ))
      val domain2 = Domain(At(), Identifier(At(), "Second"), Contents(
        Type(At(), Identifier(At(), "B"), Number(At()))
      ))
      val domain3 = Domain(At(), Identifier(At(), "Third"), Contents(
        Type(At(), Identifier(At(), "C"), Bool(At()))
      ))
      val root = Root(At(), Contents(domain1, domain2, domain3))

      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
      val bastBytes = output.bytes

      BASTReader.read(bastBytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 3
          succeed
        case Left(errs) =>
          fail(s"BAST read failed: ${errs.format}")
      }
    }

    "preserve string table data" in {
      // Build AST with repeated strings (String type appears multiple times)
      val types: Seq[Type] = Seq(
        Type(At(), Identifier(At(), "Repeated"), String_(At())),
        Type(At(), Identifier(At(), "AlsoString"), String_(At())),
        Type(At(), Identifier(At(), "MoreString"), String_(At()))
      )
      val domain = Domain(At(), Identifier(At(), "StringTest"), Contents(types*))
      val root = Root(At(), Contents(domain))

      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

      // String table should have interned common strings
      output.stringTableSize must be > 0

      BASTReader.read(output.bytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
          succeed
        case Left(errs) =>
          fail(s"BAST read failed: ${errs.format}")
      }
    }

    "handle alternation types" in {
      // Build AST with alternation (union) type
      val altExpr = Alternation(At(), Contents(
        AliasedTypeExpression(At(), "type", PathIdentifier(At(), Seq("String"))),
        AliasedTypeExpression(At(), "type", PathIdentifier(At(), Seq("Number")))
      ))
      val altType = Type(At(), Identifier(At(), "StringOrNumber"), altExpr)
      val domain = Domain(At(), Identifier(At(), "AltDomain"), Contents(altType))
      val root = Root(At(), Contents(domain))

      val passInput = PassInput(root)
      val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
      val bastBytes = output.bytes

      BASTReader.read(bastBytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
          succeed
        case Left(errs) =>
          fail(s"BAST read failed: ${errs.format}")
      }
    }
  }
}
