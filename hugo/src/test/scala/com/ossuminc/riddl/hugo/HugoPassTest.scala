package com.ossuminc.riddl.hugo

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.passes.{Pass, PassCreators, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.TestingBasis

class HugoPassTest extends TestingBasis {

  "HugoOutput" must {
    "construct empty" in {
      val ho = HugoOutput()
      ho.messages must be(empty)
    }
  }

  "HugoPass.Options" must {
    "construct empty" in {
      val hpo = HugoPass.Options()
      hpo.baseUrl must be(Option(java.net.URI.create("https://example.com/").toURL))
      hpo.themes must be(Seq("hugo-geekdoc" -> Option(HugoPass.geekDoc_url)))
      hpo.sourceURL must be(None)
      hpo.editPath must be(Some("edit/main/src/main/riddl"))
      hpo.viewPath must be(Some("blob/main/src/main/riddl"))
      hpo.withGlossary must be(true)
      hpo.withTODOList must be(true)
      hpo.withGraphicalTOC must be(false)
      hpo.withStatistics must be(true)
      hpo.withMessageSummary must be(true)
      hpo.configFile.getFileName.toString must be("config.toml")
    }

  }

  "HugoPass" must {
    "have correct name" in {
      HugoPass.name must be("hugo")
    }
    "get no passes with false options" in {
      val hpo = HugoPass
        .Options()
        .copy(withGlossary = false, withTODOList = false, withGraphicalTOC = false, withMessageSummary = false)
      val passes: PassCreators = HugoPass.getPasses(hpo)
      passes.size mustBe(6)
    }
    "check its creation dependencies" in {
      val input = PassInput(Root())
      val output = PassesOutput()
      val thrown = intercept[IllegalArgumentException] { HugoPass.creator(HugoPass.Options())(input, output) }
      thrown.isInstanceOf[IllegalArgumentException] must be(true)
    }
  }
}
