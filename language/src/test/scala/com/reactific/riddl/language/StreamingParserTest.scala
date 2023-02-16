/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For StreamingParser */
class StreamingParserTest extends ParsingTest {

  val sourceInput =
    """source GetWeatherForecast is {
      |  outlet Weather is Forecast
      |} brief "foo" described by "This is a source for Forecast data"
      |""".stripMargin
  def sourceExpected(rpi: RiddlParserInput, col: Int = 0, row: Int = 0) =
    Processor(
      (row + 1, col + 1, rpi),
      Identifier((row + 1, col + 8, rpi), "GetWeatherForecast"),
      Source((row + 1, col + 1, rpi)),
      List.empty[Inlet],
      List(Outlet(
        (row + 2, 3, rpi),
        Identifier((row + 2, 10, rpi), "Weather"),
        TypeRef(
          (row + 2, 21, rpi),
          PathIdentifier((row + 2, 21, rpi), List("Forecast"))
        )
      )),
      List.empty[Handler],
      Seq.empty[Include[ProcessorDefinition]],
      Seq.empty[AuthorRef],
      Seq.empty[ProcessorOption],
      Seq.empty[Term],
      Some(LiteralString((row + 3, 9, rpi), "foo")),
      Option(BlockDescription(
        (row + 3, 28, rpi),
        List(LiteralString(
          (row + 3, 28, rpi),
          "This is a source for Forecast " + "data"
        ))
      ))
    )

  "StreamingParser" should {
    "recognize a source processor" in {
      val rpi = RiddlParserInput(sourceInput)
      checkDefinition[Processor, Processor](rpi, sourceExpected(rpi), identity)
    }
    "recognize a source processor in a context" in {
      val input = s"context foo is { $sourceInput }"
      val rpi = RiddlParserInput(input)
      val expected = Context(
        (1, 1, rpi),
        Identifier((1, 9, rpi), "foo"),
        processors = Seq(sourceExpected(rpi, 17))
      )
      checkDefinition[Context, Context](rpi, expected, identity)
    }
    "recognize a Pipe" in {
      val rpi = RiddlParserInput("""
                                   |pipe TemperatureChanges is {
                                   |  transmit temperature
                                   |   from outlet Thinga.MaInnie
                                   |   to inlet Thinga.MaOutie
                                   |}
                                   |""".stripMargin)
      val expected = Pipe(
        (1, 1, rpi),
        Identifier((2, 6, rpi), "TemperatureChanges"),
        Seq.empty,
        Option(TypeRef(
          (3, 12, rpi),
          PathIdentifier((3, 12, rpi), List("temperature"))
        )),
        Some(OutletRef(
          (4, 9, rpi),
          PathIdentifier((4, 16, rpi), List("Thinga", "MaInnie"))
        )),
        Some(InletRef(
          (5, 7, rpi),
          PathIdentifier((5, 13, rpi), List("Thinga", "MaOutie"))
        )),
        None,
        None
      )
      checkDefinition[Pipe, Pipe](rpi, expected, identity)
    }

    "recognize a streaming context" in {
      val rpi = RiddlParserInput(
        """
          |domain AnyDomain is {
          |context SensorMaintenance is {
          |
          |  source GetWeatherForecast is {
          |    outlet Weather is Forecast
          |  } described by "This is a source for Forecast data"
          |
          |  flow GetCurrentTemperature is {
          |    inlet Weather is Forecast
          |    outlet CurrentTemp is Temperature
          |  } explained as "This is a Flow for the current temperature, when it changes"
          |
          |  sink AttenuateSensor is {
          |    inlet CurrentTemp is Temperature
          |  } explained as "This is a Sink for making sensor adjustments based on temperature"
          |
          |  pipe WeatherForecast is {
          |    transmit Forecast
          |    from outlet GetWeatherForecast.Weather
          |    to inlet GetCurrentTemperature.weather
          |  } explained as "Carries changes in the current weather forecast"
          |
          |  pipe TemperatureChanges is {
          |    transmit temperature
          |    from outlet GetCurrentTemperature.CurrentTemp
          |    to inlet AttenuateSensor.CurrentTemp
          |  } explained as "Carries changes in the current temperature"
          |
          |} explained as
          |"A complete plant definition for temperature based sensor attenuation."
          |
          |} explained as "Plants can only be specified in a domain definition"
          |""".stripMargin
      )
      val expected = Context(
        loc = (3, 1, rpi),
        id = Identifier((3, 9, rpi), "SensorMaintenance"),
        pipes = List(
          Pipe(
            (18, 3, rpi),
            Identifier((18, 8, rpi), "WeatherForecast"),
            Seq.empty[PipeOption],
            Option(TypeRef(
              (19, 14, rpi),
              PathIdentifier((19, 14, rpi), List("Forecast"))
            )),
            Option(OutletRef(
              (20, 10, rpi),
              PathIdentifier(
                (20, 17, rpi),
                List("GetWeatherForecast", "Weather")
              )
            )),
            Some(InletRef(
              (21, 8, rpi),
              PathIdentifier(
                (21, 14, rpi),
                List("GetCurrentTemperature", "weather")
              )
            )),
            None,
            Option(BlockDescription(
              (22, 18, rpi),
              List(LiteralString(
                (22, 18, rpi),
                "Carries changes in the current weather forecast"
              ))
            ))
          ),
          Pipe(
            (24, 3, rpi),
            Identifier((24, 8, rpi), "TemperatureChanges"),
            Seq.empty[PipeOption],
            Option(TypeRef(
              (25, 14, rpi),
              PathIdentifier((25, 14, rpi), List("temperature"))
            )),
            Option(OutletRef(
              (26, 10, rpi),
              PathIdentifier(
                (26, 17, rpi),
                List("GetCurrentTemperature", "CurrentTemp")
              )
            )),
            Option(InletRef(
              (27, 8, rpi),
              PathIdentifier(
                (27, 14, rpi),
                List("AttenuateSensor", "CurrentTemp")
              )
            )),
            None,
            Option(BlockDescription(
              (28, 18, rpi),
              List(LiteralString(
                (28, 18, rpi),
                "Carries changes in the current temperature"
              ))
            ))
          )
        ),
        processors = List(
          Processor(
            (5, 3, rpi),
            Identifier((5, 10, rpi), "GetWeatherForecast"),
            Source((5, 3, rpi)),
            List(),
            List(Outlet(
              (6, 5, rpi),
              Identifier((6, 12, rpi), "Weather"),
              TypeRef(
                (6, 23, rpi),
                PathIdentifier((6, 23, rpi), List("Forecast"))
              ),
              None
            )),
            List.empty[Handler],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[AuthorRef],
            Seq.empty[ProcessorOption],
            Seq.empty[Term],
            None,
            Option(BlockDescription(
              (7, 18, rpi),
              List(LiteralString(
                (7, 18, rpi),
                "This is a source for Forecast data"
              ))
            ))
          ),
          Processor(
            (9, 3, rpi),
            Identifier((9, 8, rpi), "GetCurrentTemperature"),
            Flow((9, 3, rpi)),
            List(Inlet(
              (10, 5, rpi),
              Identifier((10, 11, rpi), "Weather"),
              TypeRef(
                (10, 22, rpi),
                PathIdentifier((10, 22, rpi), List("Forecast"))
              ),
              None
            )),
            List(Outlet(
              (11, 5, rpi),
              Identifier((11, 12, rpi), "CurrentTemp"),
              TypeRef(
                (11, 27, rpi),
                PathIdentifier((11, 27, rpi), List("Temperature"))
              ),
              None
            )),
            List.empty[Handler],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[AuthorRef],
            Seq.empty[ProcessorOption],
            Seq.empty[Term],
            None,
            Option(BlockDescription(
              (12, 18, rpi),
              List(LiteralString(
                (12, 18, rpi),
                "This is a Flow for the current temperature, when it changes"
              ))
            ))
          ),
          Processor(
            (14, 3, rpi),
            Identifier((14, 8, rpi), "AttenuateSensor"),
            Sink((14, 3, rpi)),
            List(Inlet(
              (15, 5, rpi),
              Identifier((15, 11, rpi), "CurrentTemp"),
              TypeRef(
                (15, 26, rpi),
                PathIdentifier((15, 26, rpi), List("Temperature"))
              ),
              None
            )),
            List.empty[Outlet],
            List.empty[Handler],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[AuthorRef],
            Seq.empty[ProcessorOption],
            Seq.empty[Term],
            None,
            Option(BlockDescription(
              (16, 18, rpi),
              List(LiteralString(
                (16, 18, rpi),
                "This is a Sink for making sensor adjustments based on temperature"
              ))
            ))
          )
        ),
        description = Option(BlockDescription(
          (31, 1, rpi),
          List(LiteralString(
            (31, 1, rpi),
            "A complete plant definition for temperature based sensor attenuation."
          ))
        ))
      )
      checkDefinition[Domain, Context](rpi, expected, _.contexts.head)
    }
  }
}
