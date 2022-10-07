package docs

import com.reactific.riddl.translator.hugo.RunHugoTestBase
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends AnyWordSpec with RunHugoTestBase {

  "Docs" should {
    "run successfully with hugo" in {
      val srcDir = Path.of("doc/src/hugo")
      runHugo(srcDir)
    }
  }
}
