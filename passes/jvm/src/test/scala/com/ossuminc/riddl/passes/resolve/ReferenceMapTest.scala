package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec, Await}
import com.ossuminc.riddl.utils.PathUtils

import java.nio.file.Path
import org.scalatest.TestData

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ReferenceMapTest extends AbstractValidatingTest {

  protected def create: Future[PassesResult] = {
    val url = PathUtils.urlFromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"))
    RiddlParserInput.fromURL(url).map { rpi =>
      simpleParseAndValidate(rpi) match {
        case Left(messages) => fail(messages.format)
        case Right(result)  => result
      }
    }
  }

  "ReferenceMap" must {
    val result: PassesResult = Await.result(create, 10.seconds)
    val refMap = result.refMap

    "convert to a pretty string" in { _ =>
      info("pretty: " + refMap.toString)
      refMap.toString must not be empty
    }
    "have correct size" in { _ =>
      info("size: " + refMap.size.toString)
      refMap.size must be(31)
    }

    "have definitionOf(pathId:String) work" in { _ =>
      refMap.definitionOf[Author]("Reid") match {
        case None                 => fail("Expected to find Author 'Reid'")
        case Some(author: Author) => author.name.s mustBe "Reid"
        case x                    => fail(s"Unexpected result: ${x.toString}")
      }
    }

    "inserts a value and finds it" in { _ =>
      val context: Context = Context(At(), Identifier(At(), "context"))
      val parent: Parent = Domain(At(), Identifier(At(), "domain"))
      val pid = PathIdentifier(At(), Seq("wrong-name"))
      refMap.add[Context](pid, parent, context)
      refMap.definitionOf[Context](pid, parent) must not be empty
    }

    "have definitionOf(pid: PathIdentifier, parent: Parent) work" in { _ =>
      val pid = PathIdentifier(At.empty, Seq("Sink", "Commands"))
      val context = result.root.domains.head.includes.head.contents.filter[Context].head
      val parent = context.connectors.head
      parent.id.value mustBe "AChannel"
      refMap.definitionOf[Inlet](pid) match {
        case Some(actual: Inlet) =>
          actual.id.value mustBe ("Commands")
          val expected = context.streamlets.find("Sink")
          expected match {
            case Some(streamlet: Streamlet) =>
              streamlet.id.value mustBe ("Sink")
              streamlet.inlets must (not be (empty))
              val expected = streamlet.inlets.head
              actual mustBe expected
            case None => fail("Didn't find streamlets 'Sink'")
            case x    => fail(s"Unexpected result: ${x.toString}")
          }
        case None => fail("Expected to find 'Source'")
        case x    => fail(s"Unexpected result: ${x.toString}")
      }
    }

    "have definitionOf(ref: References[T], parent: Parent) work" in { (td: TestData) =>
      val context = result.root.domains.head.includes(1).contents.filter[Context].head
      val entity = context.entities.head
      val expected = entity.types(2)
      val pid = PathIdentifier(At.empty, Seq("Something", "someData"))
      val ref = TypeRef(At(), "record", pid)
      refMap.definitionOf[Type](ref, entity) match {
        case Some(actual: Type) =>
          actual mustBe expected
          actual.id.value mustBe ("someData")
        case None => fail("Expected to find 'Something'")
        case x    => fail(s"Unexpected result: ${x.toString}")
      }
    }
  }

}
