package com.yoppworks.ossum.riddl.translator.git

import com.yoppworks.ossum.riddl.language.{TranslatingTestBase, Translator}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslatingOptions

import java.nio.file.Path
import scala.concurrent.duration.DurationInt
class GitTranslatorTest extends TranslatingTestBase[GitTranslatorOptions]  {

  override val output: String = "git-translator/target/test"

  override def makeTranslatorOptions(fileName: String): GitTranslatorOptions = {
    val hugoOptions = HugoTranslatingOptions(
      inputFile = Some(makeInputFile(fileName)),
      outputDir = Some(Path.of(output))
    )
    val gitCloneDir = Path.of(".").toAbsolutePath
    val refreshRate = 3.seconds
    GitTranslatorOptions(
      Some(commonOptions), Some(validatingOptions), Some(hugoOptions),
      Some(gitCloneDir), refreshRate
    )
  }

  override def getTranslator: Translator[GitTranslatorOptions] = GitTranslator

  "GitTranslatorTest" should {
    runTests("GitTranslatorTest")
  }
}
