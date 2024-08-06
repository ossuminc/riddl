package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At

class ProjectionsTest extends ParsingTest {

  "Projector" should {
    "use a Repository" in {
      val rpi = RiddlParserInput("""domain ignore {
        |  context ignore {
        |    repository storage is {
        |       ???
        |    }
        |    projector transform is {
        |       updates repository storage
        |    }
        |  }
        |}
        |""".stripMargin)
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match
        case Left(messages) =>
          if messages.justErrors.nonEmpty then fail(messages.format)
          succeed
        case Right(proj: Projector, input) =>
          val rrs = proj.contents.filter[RepositoryRef]
          rrs mustNot be(empty)
          val rr: RepositoryRef = rrs.head
          rr.pathId mustBe PathIdentifier(At(7, 27, input), Seq("storage"))

    }
    "does not use data statements" in {
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |   projector transform is {
          |     command PutIt { field: Integer }
          |     record Foo { data: String }
          |     handler X is {
          |       on command PutIt {
          |         put "thing" to record Foo
          |       }
          |     }
          |   }
          |""".stripMargin
      )
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match {
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.isEmpty then fail("Should have generated an error")
          succeed
        case Right(_, _) =>
          fail("Should have failed")
      }
    }
  }

  "Repository" should {
    "have a schema" in {
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
          |""".stripMargin
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
    "handles data statements" in {
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
          |""".stripMargin
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
