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
      List.empty[Example],
      Seq.empty[Include[ProcessorDefinition]],
      Seq.empty[Author],
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
      val rpi =
        RiddlParserInput("""
                           |pipe TemperatureChanges is { transmit temperature }
                           |""".stripMargin)
      val expected = Pipe(
        (1, 1, rpi),
        Identifier((2, 6, rpi), "TemperatureChanges"),
        Option(TypeRef(
          (2, 39, rpi),
          PathIdentifier((2, 39, rpi), List("temperature"))
        ))
      )
      checkDefinition[Pipe, Pipe](rpi, expected, identity)
    }

    "recognize an InJoint" in {
      val input =
        """joint temp_in is inlet GetCurrentTemperature.weather from pipe WeatherForecast"""
      val expected = InletJoint(
        1 -> 1,
        Identifier(1 -> 7, "temp_in"),
        InletRef(
          1 -> 18,
          PathIdentifier(1 -> 24, Seq("GetCurrentTemperature", "weather"))
        ),
        PipeRef(1 -> 59, PathIdentifier(1 -> 64, Seq("WeatherForecast")))
      )
      checkDefinition[InletJoint, InletJoint](input, expected, identity)
    }
    "recognize an OutJoint" in {
      val input =
        """joint forecast is outlet GetWeatherForecast.Weather to pipe WeatherForecast"""
      val expected = OutletJoint(
        1 -> 1,
        Identifier(1 -> 7, "forecast"),
        OutletRef(
          1 -> 19,
          PathIdentifier(1 -> 26, Seq("GetWeatherForecast", "Weather"))
        ),
        PipeRef(1 -> 56, PathIdentifier(1 -> 61, Seq("WeatherForecast")))
      )
      checkDefinition[OutletJoint, OutletJoint](input, expected, identity)
    }
    "recognize a Plant" in {
      val rpi = RiddlParserInput(
        """
          |domain AnyDomain is {
          |plant SensorMaintenance is {
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
          |  } explained as "Carries changes in the current weather forecast"
          |
          |  pipe TemperatureChanges is {
          |    transmit temperature
          |  } explained as "Carries changes in the current temperature"
          |
          |  joint forecast is outlet GetWeatherForecast.Weather to pipe WeatherForecast
          |  joint temp_in is inlet GetCurrentTemperature.weather from pipe WeatherForecast
          |  joint temp_out is outlet GetCurrentTemperature.CurrentTemp to pipe TemperatureChanges
          |  joint temp_changes is inlet AttenuateSensor.CurrentTemp from pipe TemperatureChanges
          |
          |} explained as
          |"A complete plant definition for temperature based sensor attenuation."
          |
          |} explained as "Plants can only be specified in a domain definition"
          |""".stripMargin
      )
      val expected = Plant(
        (3, 1, rpi),
        Identifier((3, 7, rpi), "SensorMaintenance"),
        List(
          Pipe(
            (18, 3, rpi),
            Identifier((18, 8, rpi), "WeatherForecast"),
            Option(TypeRef(
              (19, 14, rpi),
              PathIdentifier((19, 14, rpi), List("Forecast"))
            )),
            None,
            Option(BlockDescription(
              (20, 18, rpi),
              List(LiteralString(
                (20, 18, rpi),
                "Carries changes in the current weather forecast"
              ))
            ))
          ),
          Pipe(
            (22, 3, rpi),
            Identifier((22, 8, rpi), "TemperatureChanges"),
            Option(TypeRef(
              (23, 14, rpi),
              PathIdentifier((23, 14, rpi), List("temperature"))
            )),
            None,
            Option(BlockDescription(
              (24, 18, rpi),
              List(LiteralString(
                (24, 18, rpi),
                "Carries changes in the current temperature"
              ))
            ))
          )
        ),
        List(
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
            List.empty[Example],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[Author],
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
            List.empty[Example],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[Author],
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
            List.empty[Example],
            Seq.empty[Include[ProcessorDefinition]],
            Seq.empty[Author],
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
        List(
          InletJoint(
            (27, 3, rpi),
            Identifier((27, 9, rpi), "temp_in"),
            InletRef(
              (27, 20, rpi),
              PathIdentifier(
                (27, 26, rpi),
                List("GetCurrentTemperature", "weather")
              )
            ),
            PipeRef(
              (27, 61, rpi),
              PathIdentifier((27, 66, rpi), List("WeatherForecast"))
            ),
            None
          ),
          InletJoint(
            (29, 3, rpi),
            Identifier((29, 9, rpi), "temp_changes"),
            InletRef(
              (29, 25, rpi),
              PathIdentifier(
                (29, 31, rpi),
                List("AttenuateSensor", "CurrentTemp")
              )
            ),
            PipeRef(
              (29, 64, rpi),
              PathIdentifier((29, 69, rpi), List("TemperatureChanges"))
            ),
            None
          )
        ),
        List(
          OutletJoint(
            (26, 3, rpi),
            Identifier((26, 9, rpi), "forecast"),
            OutletRef(
              (26, 21, rpi),
              PathIdentifier(
                (26, 28, rpi),
                List("GetWeatherForecast", "Weather")
              )
            ),
            PipeRef(
              (26, 58, rpi),
              PathIdentifier((26, 63, rpi), List("WeatherForecast"))
            ),
            None
          ),
          OutletJoint(
            (28, 3, rpi),
            Identifier((28, 9, rpi), "temp_out"),
            OutletRef(
              (28, 21, rpi),
              PathIdentifier(
                (28, 28, rpi),
                List("GetCurrentTemperature", "CurrentTemp")
              )
            ),
            PipeRef(
              (28, 65, rpi),
              PathIdentifier((28, 70, rpi), List("TemperatureChanges"))
            ),
            None
          )
        ),
        Seq.empty[Term],
        Seq.empty[Include[PlantDefinition]],
        Seq.empty[Author],
        Seq.empty[PlantOption],
        None,
        Option(BlockDescription(
          (32, 1, rpi),
          List(LiteralString(
            (32, 1, rpi),
            "A complete plant definition for temperature based sensor attenuation."
          ))
        ))
      )
      checkDefinition[Domain, Plant](rpi, expected, _.plants.head)
    }
  }
}
