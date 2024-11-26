/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.diagrams.mermaid.RootOverviewDiagram
import com.ossuminc.riddl.passes.AbstractRunPassTest
import com.ossuminc.riddl.passes.diagrams.DiagramsPass
import com.ossuminc.riddl.utils.{Await, URL}
import com.ossuminc.riddl.utils.{ec, pc}

import org.scalatest.TestData
import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class RootOverviewDiagramTest extends AbstractRunPassTest {

  "RootOverviewDiagram" should {
    "generate a simple diagram correctly" in { (_:TestData) =>
      val url = URL.fromCwdPath("hugo/src/test/input/context-relationships.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        val result = runPassesWith(rpi, DiagramsPass.creator())
        val diagram = RootOverviewDiagram(result.root)
        val lines = diagram.generate
        lines mustNot be(empty)
        val actual = lines.mkString("\n")
        val expected: String =
          """---
            |title: Root Overview
            |init:
            |    theme: dark
            |flowchart:
            |    defaultRenderer: elk
            |    width: 100%
            |    useMaxWidth: true
            |    securityLevel: loose
            |---
            |
            |flowchart TB
            |  classDef default fill:#666,stroke:black,stroke-width:3px,color:white;
            |  classDef bar_class color:white,stroke-width:3px;
            |  subgraph 'Domain 'bar''
            |    direction TB
            |  end
            |  classDef D_class fill:white,stroke:#333,stroke-width:3px,color:red;
            |  subgraph 'bar-Contexts'
            |    direction TB
            |    bar((Domain 'bar'))
            |    D((fa:fa-house<br/>Context 'D'))
            |    bar-->|contains|D((fa:fa-house<br/>Context 'D'))
            |  end
            |  subgraph 'bar-Applications'
            |    direction TB
            |    bar((Domain 'bar'))
            |  end
            |  subgraph 'bar-Epics'
            |    direction TB
            |    bar((Domain 'bar'))
            |  end
            |  class D D_class
            |  classDef foo_class color:white,stroke-width:3px;
            |  subgraph 'Domain 'foo''
            |    direction TB
            |  end
            |  classDef A_class fill:white,stroke:#333,stroke-width:3px,color:blue;
            |  classDef B_class fill:white,stroke:#333,stroke-width:3px,color:green;
            |  classDef C_class fill:white,stroke:#333,stroke-width:3px,color:purple;
            |  subgraph 'foo-Contexts'
            |    direction TB
            |    foo((Domain 'foo'))
            |    A((fa:fa-house<br/>Context 'A'))
            |    B((fa:fa-house<br/>Context 'B'))
            |    C((fa:fa-house<br/>Context 'C'))
            |    foo-->|contains|A((fa:fa-house<br/>Context 'A'))
            |    foo-->|contains|B((fa:fa-house<br/>Context 'B'))
            |    foo-->|contains|C((fa:fa-house<br/>Context 'C'))
            |  end
            |  subgraph 'foo-Applications'
            |    direction TB
            |    foo((Domain 'foo'))
            |  end
            |  subgraph 'foo-Epics'
            |    direction TB
            |    foo((Domain 'foo'))
            |  end
            |  class A A_class
            |  class B B_class
            |  class C C_class""".stripMargin
        actual mustBe expected
      }
      Await.result(future, 10.seconds)
    }
  }
}
