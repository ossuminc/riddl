/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}
import org.scalatest.TestData

/** Unit tests for BAST serialization at the language module level.
  *
  * These tests verify BASTWriter and BASTReader work correctly without
  * depending on the Pass framework. They use a simple manual traversal
  * to serialize AST nodes, then verify BASTReader can deserialize them.
  */
class BASTSerializationTest extends AbstractTestingBasis {

  /** Simple manual serialization that mirrors what BASTWriterPass does,
    * but without depending on the passes module.
    */
  private def serializeToBASTBytes(root: Root): Array[Byte] = {
    val bw = BASTWriter()
    bw.reserveHeader()

    // Write the root (Nebula wrapper)
    bw.writeNode(root)

    // Manually traverse and write all contents
    def traverseAndWrite(value: RiddlValue): Unit = {
      value match {
        case root: Root =>
          root.contents.foreach(traverseAndWrite)

        case domain: Domain =>
          bw.writeNode(domain)
          domain.contents.foreach(traverseAndWrite)
          if domain.metadata.nonEmpty then
            bw.writeMetadataCount(domain.metadata)

        case context: Context =>
          bw.writeNode(context)
          context.contents.foreach(traverseAndWrite)
          if context.metadata.nonEmpty then
            bw.writeMetadataCount(context.metadata)

        case entity: Entity =>
          bw.writeNode(entity)
          entity.contents.foreach(traverseAndWrite)
          if entity.metadata.nonEmpty then
            bw.writeMetadataCount(entity.metadata)

        case handler: Handler =>
          bw.writeNode(handler)
          handler.contents.foreach(traverseAndWrite)
          if handler.metadata.nonEmpty then
            bw.writeMetadataCount(handler.metadata)

        case state: State =>
          bw.writeNode(state)
          state.contents.foreach(traverseAndWrite)
          if state.metadata.nonEmpty then
            bw.writeMetadataCount(state.metadata)

        case t: Type =>
          bw.writeNode(t)
          if t.metadata.nonEmpty then
            bw.writeMetadataCount(t.metadata)

        case other =>
          bw.writeNode(other)
      }
    }

    traverseAndWrite(root)

    val stringTableOffset = bw.writeStringTable()
    bw.finalize(stringTableOffset)
  }

  "BAST Serialization" should {

    "round-trip a simple domain with a type" in { (_: TestData) =>
      val typeDef = Type(At(), Identifier(At(), "Foo"), String_(At()))
      val domain = Domain(At(), Identifier(At(), "Simple"),
        Contents(typeDef))
      val root = Root(At(), Contents(domain))

      val bytes = serializeToBASTBytes(root)
      bytes.length must be > 0

      BASTReader.read(bytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
        case Left(errors) =>
          fail(s"BAST read failed: ${errors.format}")
      }
    }

    "round-trip domain with context and entity" in { (_: TestData) =>
      val handler = Handler(At(), Identifier(At(), "TestHandler"))
      val entity = Entity(At(), Identifier(At(), "TestEntity"),
        Contents(handler))
      val context = Context(At(), Identifier(At(), "TestCtx"),
        Contents(entity))
      val domain = Domain(At(), Identifier(At(), "TestDomain"),
        Contents(context))
      val root = Root(At(), Contents(domain))

      val bytes = serializeToBASTBytes(root)

      BASTReader.read(bytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
        case Left(errors) =>
          fail(s"BAST read failed: ${errors.format}")
      }
    }

    "round-trip entity with state containing handlers" in {
      (_: TestData) =>
        val stateHandler = Handler(At(),
          Identifier(At(), "StateHandler"))
        val typRef = TypeRef(At(),
          "type", PathIdentifier(At(), Seq("MyFields")))
        val state = State(At(), Identifier(At(), "Active"),
          typRef, Contents(stateHandler))
        val entity = Entity(At(), Identifier(At(), "MyEntity"),
          Contents(state))
        val context = Context(At(), Identifier(At(), "MyCtx"),
          Contents(entity))
        val domain = Domain(At(), Identifier(At(), "MyDomain"),
          Contents(context))
        val root = Root(At(), Contents(domain))

        val bytes = serializeToBASTBytes(root)

        BASTReader.read(bytes) match {
          case Right(nebula: Nebula) =>
            nebula.contents.toSeq.size mustBe 1
          case Left(errors) =>
            fail(s"BAST read failed: ${errors.format}")
        }
    }

    "round-trip various type expressions" in { (_: TestData) =>
      val types: Seq[Type] = Seq(
        Type(At(), Identifier(At(), "AString"), String_(At())),
        Type(At(), Identifier(At(), "ANumber"), Number(At())),
        Type(At(), Identifier(At(), "ABool"), Bool(At())),
        Type(At(), Identifier(At(), "AnOptional"),
          Optional(At(), String_(At()))),
        Type(At(), Identifier(At(), "AList"),
          OneOrMore(At(), String_(At()))),
        Type(At(), Identifier(At(), "AMapping"),
          Mapping(At(), String_(At()), Number(At())))
      )
      val domain = Domain(At(), Identifier(At(), "TypesDomain"),
        Contents(types*))
      val root = Root(At(), Contents(domain))

      val bytes = serializeToBASTBytes(root)

      BASTReader.read(bytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 1
        case Left(errors) =>
          fail(s"BAST read failed: ${errors.format}")
      }
    }

    "round-trip multiple domains" in { (_: TestData) =>
      val d1 = Domain(At(), Identifier(At(), "First"),
        Contents(Type(At(), Identifier(At(), "A"), String_(At()))))
      val d2 = Domain(At(), Identifier(At(), "Second"),
        Contents(Type(At(), Identifier(At(), "B"), Number(At()))))
      val d3 = Domain(At(), Identifier(At(), "Third"),
        Contents(Type(At(), Identifier(At(), "C"), Bool(At()))))
      val root = Root(At(), Contents(d1, d2, d3))

      val bytes = serializeToBASTBytes(root)

      BASTReader.read(bytes) match {
        case Right(nebula: Nebula) =>
          nebula.contents.toSeq.size mustBe 3
        case Left(errors) =>
          fail(s"BAST read failed: ${errors.format}")
      }
    }

    "reject BAST with stale format revision" in { (_: TestData) =>
      val domain = Domain(At(), Identifier(At(), "RevTest"),
        Contents(Type(At(), Identifier(At(), "X"), String_(At()))))
      val root = Root(At(), Contents(domain))
      val bytes = serializeToBASTBytes(root).clone()

      // Patch format revision to 0 (offset 10-11 in header)
      bytes(10) = 0.toByte
      bytes(11) = 0.toByte

      BASTReader.read(bytes) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          msg must include("format revision")
          msg must include("regenerate")
        case Right(_) =>
          fail("Expected rejection of stale format revision")
      }
    }

    "reject BAST with future format revision" in { (_: TestData) =>
      val domain = Domain(At(), Identifier(At(), "RevTest"),
        Contents(Type(At(), Identifier(At(), "X"), String_(At()))))
      val root = Root(At(), Contents(domain))
      val bytes = serializeToBASTBytes(root).clone()

      // Patch format revision to 99 (offset 10-11 in header)
      bytes(10) = 0.toByte
      bytes(11) = 99.toByte

      BASTReader.read(bytes) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          msg must include("format revision")
        case Right(_) =>
          fail("Expected rejection of future format revision")
      }
    }

    "accept BAST with current format revision" in {
      (_: TestData) =>
        val domain = Domain(At(), Identifier(At(), "RevOK"),
          Contents(Type(At(), Identifier(At(), "Y"), Bool(At()))))
        val root = Root(At(), Contents(domain))
        val bytes = serializeToBASTBytes(root)

        BASTReader.read(bytes) match {
          case Right(nebula: Nebula) =>
            nebula.contents.toSeq.size mustBe 1
          case Left(errors) =>
            fail(s"Should accept current revision: ${errors.format}")
        }
    }

    "reject invalid magic bytes" in { (_: TestData) =>
      val domain = Domain(At(), Identifier(At(), "MagicTest"),
        Contents(Type(At(), Identifier(At(), "X"), String_(At()))))
      val root = Root(At(), Contents(domain))
      val bytes = serializeToBASTBytes(root).clone()

      // Corrupt magic bytes (first 4 bytes)
      bytes(0) = 'X'.toByte

      BASTReader.read(bytes) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          msg must include("Not a BAST file")
        case Right(_) =>
          fail("Expected rejection of invalid magic bytes")
      }
    }
  }
}
