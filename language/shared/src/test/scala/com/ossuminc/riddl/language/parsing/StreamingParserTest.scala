/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Unit Tests For StreamingParser */
abstract class StreamingParserTest(using PlatformContext) extends AbstractParsingTest {

  import com.ossuminc.riddl.language.parsing.RiddlParserInput.*

  val sourceInput: String =
    """source GetWeatherForecast is {
      |  outlet Weather is command Forecast
      | } with {
      |  briefly as "foo"
      |  described as "This is a source for Forecast data"
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
      Contents(
        Outlet(
          (row + 2, 3, rpi),
          Identifier((row + 2, 10, rpi), "Weather"),
          TypeRef(
            (row + 2, 21, rpi),
            "command",
            PathIdentifier((row + 2, 29, rpi), List("Forecast"))
          )
        )
      ),
      Contents(
        BriefDescription((row + 4, 3, rpi), LiteralString((row + 4, 14, rpi), "foo")),
        BlockDescription(
          (row + 5, 3, rpi),
          List(
            LiteralString(
              (row + 5, 16, rpi),
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
    "recognize a source processor in a referent" in { (td: TestData) =>
      val input = s"referent foo is { $sourceInput }"
      val rpi = RiddlParserInput(input, td)
      val expected = Context(
        (1, 1, rpi),
        Identifier((1, 9, rpi), "foo"),
        contents = Contents(sourceExpected(rpi, 17))
      )
      checkDefinition[Context, Context](rpi, expected, identity)
    }

    "recognize a streaming referent" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """
          |domain AnyDomain is {
          |referent SensorMaintenance is {
          |  command Forecast is { ??? }
          |  command Temperature is { ??? }
          |  source GetWeatherForecast is {
          |    outlet Weather is command Forecast
          |  } with {
          |    described as "This is a source for Forecast data"
          |  }
          |
          |  flow GetCurrentTemperature is {
          |    inlet Weather is command Forecast
          |    outlet CurrentTemp is command Temperature
          |  } with {
          |    described as "This is a Flow for the current temperature, when it changes"
          |  }
          |
          |  sink AttenuateSensor is {
          |    inlet CurrentTemp is command Temperature
          |  } with {
          |    described as "This is a Sink for making sensor adjustments based on temperature"
          |  }
          |} with {
          |  described as "A complete plant definition for temperature based sensor attenuation."
          |}
          |} with {
          |  described as "Plants can only be specified in a domain definition"
          |}
          |""".stripMargin,
        td
      )
      val expected = Context(
        loc = (3, 1, rpi),
        id = Identifier((3, 9, rpi), "SensorMaintenance"),
        Contents(
          Type(
            (4, 3, rpi),
            Identifier((4, 11, rpi), "Forecast"),
            AggregateUseCaseTypeExpression((4, 23, rpi), CommandCase, Contents.empty)
          ),
          Type(
            (5, 3, rpi),
            Identifier((5, 11, rpi), "Temperature"),
            AggregateUseCaseTypeExpression((5, 26, rpi), CommandCase, Contents.empty)
          ),
          Streamlet(
            (6, 3, rpi),
            Identifier((6, 10, rpi), "GetWeatherForecast"),
            Source((6, 3, rpi)),
            Contents(
              Outlet(
                (7, 5, rpi),
                Identifier((7, 12, rpi), "Weather"),
                TypeRef(
                  (7, 23, rpi),
                  "command",
                  PathIdentifier((7, 31, rpi), List("Forecast"))
                )
              )
            ),
            Contents(
              BlockDescription(
                (9, 5, rpi),
                List(
                  LiteralString(
                    (9, 18, rpi),
                    "This is a source for Forecast data"
                  )
                )
              )
            )
          ),
          Streamlet(
            (12, 3, rpi),
            Identifier((12, 8, rpi), "GetCurrentTemperature"),
            Flow((12, 3, rpi)),
            Contents(
              Inlet(
                (13, 5, rpi),
                Identifier((13, 11, rpi), "Weather"),
                TypeRef(
                  (13, 22, rpi),
                  "command",
                  PathIdentifier((13, 30, rpi), List("Forecast"))
                )
              ),
              Outlet(
                (14, 5, rpi),
                Identifier((14, 12, rpi), "CurrentTemp"),
                TypeRef(
                  (14, 27, rpi),
                  "command",
                  PathIdentifier((14, 35, rpi), List("Temperature"))
                )
              )
            ),
            Contents(
              BlockDescription(
                (16, 5, rpi),
                List(
                  LiteralString(
                    (16, 18, rpi),
                    "This is a Flow for the current temperature, when it changes"
                  )
                )
              )
            )
          ),
          Streamlet(
            (19, 3, rpi),
            Identifier((19, 8, rpi), "AttenuateSensor"),
            Sink((19, 3, rpi)),
            Contents(
              Inlet(
                (20, 5, rpi),
                Identifier((20, 11, rpi), "CurrentTemp"),
                TypeRef(
                  (20, 26, rpi),
                  "command",
                  PathIdentifier((20, 34, rpi), List("Temperature"))
                )
              )
            ),
            Contents(
              BlockDescription(
                (22, 5, rpi),
                List(
                  LiteralString(
                    (22, 18, rpi),
                    "This is a Sink for making sensor adjustments based on temperature"
                  )
                )
              )
            )
          )
        ),
        Contents(
          BlockDescription(
            (25, 3, rpi),
            List(
              LiteralString(
                (25, 16, rpi),
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
