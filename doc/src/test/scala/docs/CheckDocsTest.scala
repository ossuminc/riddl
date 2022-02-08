package docs

import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslateExamplesBase

import java.nio.file.Path

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends HugoTranslateExamplesBase {

  val source = "doc/src/hugo/"
  override val output: String = "doc/target/hugodoc"

  "Docs" should { "build successfully with hugo" in { runHugo(Path.of(source)) } }
}
