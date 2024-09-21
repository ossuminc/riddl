package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import org.scalatest.TestData

class ProjectorTest extends NoJVMParsingTest {

  "Projector" should {
    "use a Repository" in { (td:TestData) => 
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
        |""".stripMargin,td)
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
    "does not use data statements" in { (td:TestData) =>
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
          |""".stripMargin,
        td
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
    "can have a relationship with a repository" in {  (td:TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |    repository storage is {
          |       ???
          |    }
          |    projector transform is {
          |       updates repository storage
          |       relationship updates to repository storage as 1:1 with {
          |         brief "Just to show that this projector updates the repository"
          |       }  
          |    }
          |  }
          |}
          |""".stripMargin, td)
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match
        case Left(messages) =>
          val errors = messages.justErrors 
          if errors.nonEmpty then fail(errors.format) else succeed
        case Right(proj: Projector, input) =>
          val rels = proj.contents.filter[Relationship]
          rels mustNot be(empty)
          val rel: Relationship = rels.head
          rel.format must be("relationship updates to repository storage")
    }
  }

}
