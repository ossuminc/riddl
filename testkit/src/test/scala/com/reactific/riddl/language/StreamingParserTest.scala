package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.testkit.ParsingTest

/** Unit Tests For StreamingParser */
class StreamingParserTest extends ParsingTest {

  "StreamingParser" should {
    "recognize a source processor" in {
      val input =
        """
          |source GetWeatherForecast is {
          |  outlet Weather is Forecast
          |} brief "foo" described by "This is a source for Forecast data"
          |""".stripMargin
      val expected = Processor(
        1 -> 1,
        Identifier(2 -> 8, "GetWeatherForecast"),
        Source(1 -> 1),
        List.empty[Inlet],
        List(Outlet(
          3 -> 3,
          Identifier(3 -> 10, "Weather"),
          TypeRef(3 -> 21, PathIdentifier(3 -> 21, List("Forecast")))
        )),
        List.empty[Example],
        Some(LiteralString(4 -> 9, "foo")),
        Option(BlockDescription(
          4 -> 28,
          List(
            LiteralString(4 -> 28, "This is a source for Forecast " + "data")
          )
        ))
      )
      checkDefinition[Processor, Processor](input, expected, identity)
    }
    "recognize a Pipe" in {
      val input = """
                    |pipe TemperatureChanges is { transmit temperature }
                    |""".stripMargin
      val expected = Pipe(
        1 -> 1,
        Identifier(2 -> 6, "TemperatureChanges"),
        Option(TypeRef(2 -> 39, PathIdentifier(2 -> 39, List("temperature"))))
      )
      checkDefinition[Pipe, Pipe](input, expected, identity)
    }

    "recognize an InJoint" in {
      val input =
        """joint temp_in is inlet GetCurrentTemperature.weather from pipe WeatherForecast"""
      val expected = InletJoint(
        1 -> 1,
        Identifier(1 -> 7, "temp_in"),
        InletRef(
          1 -> 18,
          PathIdentifier(1 -> 24, Seq("weather", "GetCurrentTemperature"))
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
          PathIdentifier(1 -> 26, Seq("Weather", "GetWeatherForecast"))
        ),
        PipeRef(1 -> 56, PathIdentifier(1 -> 61, Seq("WeatherForecast")))
      )
      checkDefinition[OutletJoint, OutletJoint](input, expected, identity)
    }
    "recognize a Plant" in {
      val input =
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
      val expected = Plant(
        3 -> 1,
        Identifier(3 -> 7, "SensorMaintenance"),
        List(
          Pipe(
            18 -> 3,
            Identifier(18 -> 8, "WeatherForecast"),
            Option(
              TypeRef(19 -> 14, PathIdentifier(19 -> 14, List("Forecast")))
            ),
            None,
            Option(BlockDescription(
              20 -> 18,
              List(LiteralString(
                20 -> 18,
                "Carries changes in the current weather forecast"
              ))
            ))
          ),
          Pipe(
            22 -> 3,
            Identifier(22 -> 8, "TemperatureChanges"),
            Option(
              TypeRef(23 -> 14, PathIdentifier(23 -> 14, List("temperature")))
            ),
            None,
            Option(BlockDescription(
              24 -> 18,
              List(LiteralString(
                24 -> 18,
                "Carries changes in the current temperature"
              ))
            ))
          )
        ),
        List(
          Processor(
            5 -> 3,
            Identifier(5 -> 10, "GetWeatherForecast"),
            Source(5 -> 3),
            List(),
            List(Outlet(
              6 -> 5,
              Identifier(6 -> 12, "Weather"),
              TypeRef(6 -> 23, PathIdentifier(6 -> 23, List("Forecast"))),
              None
            )),
            List.empty[Example],
            None,
            Option(BlockDescription(
              7 -> 18,
              List(LiteralString(7 -> 18, "This is a source for Forecast data"))
            ))
          ),
          Processor(
            9 -> 3,
            Identifier(9 -> 8, "GetCurrentTemperature"),
            Flow(9 -> 3),
            List(Inlet(
              10 -> 5,
              Identifier(10 -> 11, "Weather"),
              TypeRef(10 -> 22, PathIdentifier(10 -> 22, List("Forecast"))),
              None
            )),
            List(Outlet(
              11 -> 5,
              Identifier(11 -> 12, "CurrentTemp"),
              TypeRef(11 -> 27, PathIdentifier(11 -> 27, List("Temperature"))),
              None
            )),
            List.empty[Example],
            None,
            Option(BlockDescription(
              12 -> 18,
              List(LiteralString(
                12 -> 18,
                "This is a Flow for the current temperature, when it changes"
              ))
            ))
          ),
          Processor(
            14 -> 3,
            Identifier(14 -> 8, "AttenuateSensor"),
            Sink(14 -> 3),
            List(Inlet(
              15 -> 5,
              Identifier(15 -> 11, "CurrentTemp"),
              TypeRef(15 -> 26, PathIdentifier(15 -> 26, List("Temperature"))),
              None
            )),
            List.empty[Outlet],
            List.empty[Example],
            None,
            Option(BlockDescription(
              16 -> 18,
              List(LiteralString(
                16 -> 18,
                "This is a Sink for making sensor adjustments based on temperature"
              ))
            ))
          )
        ),
        List(
          InletJoint(
            27 -> 3,
            Identifier(27 -> 9, "temp_in"),
            InletRef(
              27 -> 20,
              PathIdentifier(27 -> 26, List("weather", "GetCurrentTemperature"))
            ),
            PipeRef(
              27 -> 61,
              PathIdentifier(27 -> 66, List("WeatherForecast"))
            ),
            None
          ),
          InletJoint(
            29 -> 3,
            Identifier(29 -> 9, "temp_changes"),
            InletRef(
              29 -> 25,
              PathIdentifier(29 -> 31, List("CurrentTemp", "AttenuateSensor"))
            ),
            PipeRef(
              29 -> 64,
              PathIdentifier(29 -> 69, List("TemperatureChanges"))
            ),
            None
          )
        ),
        List(
          OutletJoint(
            26 -> 3,
            Identifier(26 -> 9, "forecast"),
            OutletRef(
              26 -> 21,
              PathIdentifier(26 -> 28, List("Weather", "GetWeatherForecast"))
            ),
            PipeRef(
              26 -> 58,
              PathIdentifier(26 -> 63, List("WeatherForecast"))
            ),
            None
          ),
          OutletJoint(
            28 -> 3,
            Identifier(28 -> 9, "temp_out"),
            OutletRef(
              28 -> 21,
              PathIdentifier(
                28 -> 28,
                List("CurrentTemp", "GetCurrentTemperature")
              )
            ),
            PipeRef(
              28 -> 65,
              PathIdentifier(28 -> 70, List("TemperatureChanges"))
            ),
            None
          )
        ),
        Seq.empty[Term],
        Seq.empty[Include],
        None,
        Option(BlockDescription(
          32 -> 1,
          List(LiteralString(
            32 -> 1,
            "A complete plant definition for temperature based sensor attenuation."
          ))
        ))
      )
      checkDefinition[Domain, Plant](input, expected, _.plants.head)
    }
  }
}

// joint forecast is outlet GetWeatherForecast.Weather to WeatherForecast
