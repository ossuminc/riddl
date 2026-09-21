/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class RepositoryTest(using PlatformContext) extends AbstractParsingTest {

  "Repository" should {
    "have a schema" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |    repository storage is {
          |      record Person is { name: String }
          |      schema structure is flat
          |        of person as record Person
          |        index on field Person.name
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Repository](rpi, _.domains.head.contexts.head.repositories.head) match
        case Left(messages) =>
          if messages.justErrors.nonEmpty then fail(messages.format)
          succeed
        case Right(repo: Repository, input) =>
          val schemas = repo.contents.filter[Schema]
          schemas mustNot be(empty)
          val schema = schemas.head
          schema mustBe Schema(
            At(5, 7, input),
            Identifier(At(5, 14, input), "structure"),
            RepositorySchemaKind.Flat,
            Map(
              Identifier(At(6, 12, input), "person") -> TypeRef(
                At(6, 22, input),
                "record",
                PathIdentifier(At(6, 29, input), Seq("Person"))
              )
            ),
            Map.empty,
            Seq(FieldRef(At(7, 18, input), PathIdentifier(At(7, 24, input), Seq("Person", "name"))))
          )
    }
    "parse keys and a history mark (B3, 2026-09-21)" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |    repository storage is {
          |      record Person is { id: String, name: String }
          |      record Pet is { id: String, owner: String }
          |      schema structure is relational
          |        of people as record Person with history
          |        of pets as record Pet
          |        link owner as field Pet.owner to field Person.id
          |        key on field Person.id
          |        key on field Pet.id
          |        index on field Person.name
          |      with { briefly "keyed" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Repository](rpi, _.domains.head.contexts.head.repositories.head) match
        case Left(messages) => fail(messages.format)
        case Right(repo: Repository, _) =>
          val schema = repo.contents.filter[Schema].head
          schema.keys.map(_.pathId.format) mustBe Seq("Person.id", "Pet.id")
          schema.history.map(_.value) mustBe Seq("people")
          schema.indices.map(_.pathId.format) mustBe Seq("Person.name")
          schema.data.size mustBe 2
          schema.metadata.nonEmpty mustBe true // the schema's own `with { }` still parsed
    }
    "handles data statements" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |   repository store_it is {
          |     command PutIt { field: Integer }
          |     record Foo { data: String }
          |     handler X is {
          |       on command PutIt {
          |         put "thing" to record Foo
          |         read "what" from record Foo
          |       }
          |     }
          |   }
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Repository](rpi, _.domains.head.contexts.head.repositories.head) match {
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.nonEmpty then succeed
          else fail(errors.format)
        case Right(_, _) =>
          succeed
      }
    }
  }
}
