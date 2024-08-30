/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST
import org.scalatest.TestData

/** Unit Tests For StreamingParser */
class StreamingParserTest extends ParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput._

  val sourceInput: String =
    """source GetWeatherForecast is {
      |  outlet Weather is command Forecast
      |  brief "foo" 
      |  described by "This is a source for Forecast data"
      |} 
      |""".stripMargin
  def sourceExpected(
    rpi: RiddlParserInput,
    col: Int = 0,
    row: Int = 0
  ): AST.Streamlet =
    Streamlet(
      (row + 1, col + 1, rpi),
      Identifier((row + 1, col + 8, rpi), "GetWeatherForecast"),
      Source((row + 1, col + 1, rpi)),
      List(
        Outlet(
          (row + 2, 3, rpi),
          Identifier((row + 2, 10, rpi), "Weather"),
          TypeRef(
            (row + 2, 21, rpi),
            "command",
            PathIdentifier((row + 2, 29, rpi), List("Forecast"))
          )
        ),
        BriefDescription((row + 3, 3, rpi), LiteralString((row + 3, 9, rpi), "foo")),
        BlockDescription(
          (row + 4, 3, rpi),
          List(
            LiteralString(
              (row + 4, 16, rpi),
              "This is a source for Forecast " + "data"
            )
          )
        )
      )
    )

  "StreamingParser" should {
    "recognize a source processor" in { (td: TestData) =>
      val rpi = RiddlParserInput(sourceInput, td)
      checkDefinition[Streamlet, Streamlet](rpi, sourceExpected(rpi), identity)
    }
    "recognize a source processor in a context" in { (td: TestData) =>
      val input = s"context foo is { $sourceInput }"
      val rpi = RiddlParserInput(input, td)
      val expected = Context(
        (1, 1, rpi),
        Identifier((1, 9, rpi), "foo"),
        contents = Seq(sourceExpected(rpi, 17))
      )
      checkDefinition[Context, Context](rpi, expected, identity)
    }

    "recognize a streaming context" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """
          |domain AnyDomain is {
          |context SensorMaintenance is {
          |  command Forecast is { ??? }
          |  command Temperature is { ??? }
          |  source GetWeatherForecast is {
          |    outlet Weather is command Forecast
          |    described by "This is a source for Forecast data"
          |  } 
          |
          |  flow GetCurrentTemperature is {
          |    inlet Weather is command Forecast
          |    outlet CurrentTemp is command Temperature
          |    explained as "This is a Flow for the current temperature, when it changes"
          |  }
          |
          |  sink AttenuateSensor is {
          |    inlet CurrentTemp is command Temperature
          |    explained as "This is a Sink for making sensor adjustments based on temperature"
          |  } 
          |  explained as "A complete plant definition for temperature based sensor attenuation."
          |} 
          |explained as "Plants can only be specified in a domain definition"
          |}
          |""".stripMargin,
        td
      )
      val expected = Context(
        loc = (3, 1, rpi),
        id = Identifier((3, 9, rpi), "SensorMaintenance"),
        List(
          Type(
            (4, 3, rpi),
            Identifier((4, 11, rpi), "Forecast"),
            AggregateUseCaseTypeExpression((4, 23, rpi), CommandCase, List())
          ),
          Type(
            (5, 3, rpi),
            Identifier((5, 11, rpi), "Temperature"),
            AggregateUseCaseTypeExpression((5, 26, rpi), CommandCase, List())
          ),
          Streamlet(
            (6, 3, rpi),
            Identifier((6, 10, rpi), "GetWeatherForecast"),
            Source((6, 3, rpi)),
            List(
              Outlet(
                (7, 5, rpi),
                Identifier((7, 12, rpi), "Weather"),
                TypeRef(
                  (7, 23, rpi),
                  "command",
                  PathIdentifier((7, 31, rpi), List("Forecast"))
                )
              ),
              BlockDescription(
                (8, 5, rpi),
                List(
                  LiteralString(
                    (8, 18, rpi),
                    "This is a source for Forecast data"
                  )
                )
              )
            )
          ),
          Streamlet(
            (11, 3, rpi),
            Identifier((11, 8, rpi), "GetCurrentTemperature"),
            Flow((11, 3, rpi)),
            List(
              Inlet(
                (12, 5, rpi),
                Identifier((12, 11, rpi), "Weather"),
                TypeRef(
                  (12, 22, rpi),
                  "command",
                  PathIdentifier((12, 30, rpi), List("Forecast"))
                )
              ),
              Outlet(
                (13, 5, rpi),
                Identifier((13, 12, rpi), "CurrentTemp"),
                TypeRef(
                  (13, 27, rpi),
                  "command",
                  PathIdentifier((13, 35, rpi), List("Temperature"))
                )
              ),
              BlockDescription(
                (14, 5, rpi),
                List(
                  LiteralString(
                    (14, 18, rpi),
                    "This is a Flow for the current temperature, when it changes"
                  )
                )
              )
            )
          ),
          Streamlet(
            (17, 3, rpi),
            Identifier((17, 8, rpi), "AttenuateSensor"),
            Sink((17, 3, rpi)),
            List(
              Inlet(
                (18, 5, rpi),
                Identifier((18, 11, rpi), "CurrentTemp"),
                TypeRef(
                  (18, 26, rpi),
                  "command",
                  PathIdentifier((18, 34, rpi), List("Temperature"))
                )
              ),
              BlockDescription(
                (19, 5, rpi),
                List(
                  LiteralString(
                    (19, 18, rpi),
                    "This is a Sink for making sensor adjustments based on temperature"
                  )
                )
              )
            )
          ),
          BlockDescription(
            (21, 3, rpi),
            List(
              LiteralString(
                (21, 16, rpi),
                "A complete plant definition for temperature based sensor attenuation."
              )
            )
          )
        )
      )
      checkDefinition[Domain, Context](rpi, expected, _.contexts.head)
    }
  }
}
