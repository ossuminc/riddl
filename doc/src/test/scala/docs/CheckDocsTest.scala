package docs

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.collection.mutable.ArrayBuffer

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends AnyWordSpec with Matchers {

  val source = "doc/src/hugo/"

  "Docs" should {
    "build successfully with hugo" in {
      import scala.sys.process._
      val srcDir = new File(source)
      srcDir.isDirectory mustBe true
      val lineBuffer: ArrayBuffer[String] = ArrayBuffer[String]()
      val logger = ProcessLogger { line => lineBuffer.append(line) }
      val proc = Process("hugo", cwd = Option(srcDir))
      proc.!(logger) match {
        case 0 =>
          succeed
        case _ =>
          fail("hugo run failed with:\n  " + lineBuffer.mkString("\n  "))
      }
    }
  }
}

