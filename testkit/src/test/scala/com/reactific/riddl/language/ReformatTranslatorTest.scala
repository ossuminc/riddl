package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.testkit.RiddlFilesTestBase
import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path

/** Test The ReformatTranslator's ability to generate consistent output */
class ReformatTranslatorTest extends RiddlFilesTestBase {


  def checkAFile(rootDir: Path, file: File): Assertion = {
    checkAFile(file, singleFile=true)
  }

  def checkAFile(
    file: File,
    singleFile: Boolean = true
  ) : Assertion = {
    val input = RiddlParserInput(file)
    parseTopLevelDomains(input) match {
      case Left(errors) =>
        val msg = errors.map(_.format).mkString
        fail(msg)
      case Right(root) =>
        val options = ReformattingOptions(inputFile = Some(file.toPath),
          singleFile = singleFile)
        val common: CommonOptions = CommonOptions()
        val log = SysLogger()
        val output = ReformatTranslator.translateToString(root, log, common,
          options)
        parseTopLevelDomains(output) match {
          case Left(errors) =>
            val message = errors.map(_.format).mkString("\n")
            fail(
              s"In '${file.getPath}': on first generation:\n" + message + "\nIn Source:\n" +
                output + "\n"
            )
          case Right(root2) =>
            val output2 = ReformatTranslator.translateToString(root2,
              log, common, options)
            parseTopLevelDomains(output2) match {
              case Left(errors) =>
                val message = errors.map(_.format).mkString("\n")
                fail(
                  s"In '${file.getPath}': on second generation: " + message + "\nIn Source:\n" +
                    output2 + "\n"
                )
              case Right(_) => output mustEqual output2
            }
        }
    }
    succeed
  }

  val items = Seq(
    "examples/src/riddl/ReactiveBBQ/ReactiveBBQ.riddl" -> false,
    "doc/src/hugo/content/language/hierarchy/domain/streaming/riddl/plant.riddl" -> false,
    "testkit/src/test/input/domains" -> true,
    "testkit/src/test/input/enumerations" -> true,
    "testkit/src/test/input/mappings" -> true,
    "testkit/src/test/input/ranges" -> true,
    "testkit/src/test/input/empty.riddl" -> false,
    "testkit/src/test/input/everything.riddl" -> false,
    "testkit/src/test/input/petstore.riddl" -> false,
    "testkit/src/test/input/rbbq.riddl" -> false
  )

  "ReformatTranslator" should {
    checkItems(items)
  }

  "ReformatTranslator2" should {
    "handle includes with singleFile==false" in {
      val input = items.head._1
      val dirFile = new File(input)
      require(dirFile.exists(), s"Test item '$input' doesn't exist!")
      checkAFile(dirFile, singleFile = false)
    }
  }
}
