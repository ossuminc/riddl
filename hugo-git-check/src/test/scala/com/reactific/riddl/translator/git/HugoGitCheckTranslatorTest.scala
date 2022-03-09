package com.reactific.riddl.translator.git

import com.reactific.riddl.language.{TranslatingTestBase, Translator}
import com.reactific.riddl.translator.hugo.HugoTranslatingOptions
import com.reactific.riddl.translator.hugo_git_check.{HugoGitCheckOptions, HugoGitCheckTranslator}

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
