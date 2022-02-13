package com.yoppworks.ossum.riddl.translator.git

import com.yoppworks.ossum.riddl.language.{TranslatingTestBase, Translator}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslatingOptions
import com.yoppworks.ossum.riddl.translator.hugo_git_check.{HugoGitCheckOptions, HugoGitCheckTranslator}

import java.nio.file.Path

class HugoGitCheckTranslatorTest extends TranslatingTestBase[HugoGitCheckOptions]  {

  override val output: String = "hugo-git-check/target/test"

  override def makeTranslatorOptions(fileName: String): HugoGitCheckOptions = {
    val hugoOptions = HugoTranslatingOptions(
      inputFile = Some(makeInputFile(fileName)),
      outputDir = Some(Path.of(output))
    )
    val gitCloneDir = Path.of(".").toAbsolutePath.getParent
    val relativeDir = Path.of(directory).resolve(fileName).getParent
    HugoGitCheckOptions(
      hugoOptions, Some(gitCloneDir), Some(relativeDir)
    )
  }

  override def getTranslator: Translator[HugoGitCheckOptions] = HugoGitCheckTranslator

  "HugoGitCheckTranslator" should {
    runTests("HugoGitCheckTranslatorTest")
  }
}
