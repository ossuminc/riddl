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
          schema mustBe Schema(At(5,7,input),Identifier(At(5,14,input),"structure"),
            RepositorySchemaKind.Flat,
            Map(Identifier(At(6,12,input),"person") -> TypeRef(At(6,22,input),"record",PathIdentifier(At(6,29,input), Seq("Person")))),
            Map.empty,
            Seq(FieldRef(At(7,18,input),PathIdentifier(At(7,24,input), Seq("Person","name"))))
          )
    }
  }
}
