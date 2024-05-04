package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.PassesResult
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class ReferenceMapTest extends ValidatingTest {

  protected def create: PassesResult = {
    val input = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
    simpleParseAndValidate(input) match {
      case Left(messages) => fail(messages.format)
      case Right(result) => result
    }
  }

  "ReferenceMap" must {
    val result: PassesResult = create
    val refMap = result.refMap
    "convert to a pretty string" in {
      info("pretty: " + refMap.toString)
    }
    "have correct size" in {
      info("size: " + refMap.size.toString)
    }

    "have definitionOf(pathId:String) work" in {
      refMap.definitionOf[Author]("Reid") match {
        case None => fail("Expected to find 'Reid'")
        case Some(author: Author) => author.name.s mustBe("Reid")
        case x => fail(s"Unexpected result: ${x.toString}")
      }
    }

    "have definitionOf(pid: PathIdentifier, parent: Parent) work" in {
      val pid = PathIdentifier(At.empty, Seq("Sink", "Commands"))
      val context = result.root.domains.head.includes.head.contents.filter[Context].head 
      val parent = context.connectors.head
      parent.id.value mustBe "AChannel"
      refMap.definitionOf[Inlet](pid, parent) match {
        case Some(actual: Inlet) =>
          actual.id.value mustBe("Commands")
          val expected = context.streamlets.find("Sink")
          expected match {
            case Some(streamlet: Streamlet) =>
              streamlet.id.value mustBe("Sink")
              streamlet.inlets must(not be(empty))
              val expected = streamlet.inlets.head
              actual mustBe expected
            case None => fail("Didn't find streamlets 'Sink'")
            case x => fail(s"Unexpected result: ${x.toString}")
          }
        case None => fail("Expected to find 'Source'")
        case x => fail(s"Unexpected result: ${x.toString}")
      }
    }

    "have definitionOf(ref: References[T], parent: Parent) work" in {
      val context = result.root.domains.head.includes(1).contents.filter[Context].head 
      val entity = context.entities.head
      val expected = entity.types(2)
      val pid = PathIdentifier(At.empty, Seq("Something", "someData"))
      val ref = TypeRef(At(), "record", pid)
      refMap.definitionOf[Type](ref, entity) match {
        case Some(actual: Type) =>
          actual mustBe expected
          actual.id.value mustBe("someData")
        case None => fail("Expected to find 'Something'")
        case x => fail(s"Unexpected result: ${x.toString}")
      }
    }
  }

}
