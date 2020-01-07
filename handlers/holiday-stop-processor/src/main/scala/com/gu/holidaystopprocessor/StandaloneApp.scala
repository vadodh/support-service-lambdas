package com.gu.holidaystopprocessor

import java.time.LocalDate

import com.gu.effects.GetFromS3
import com.gu.holiday_stops.{Config, OverallFailure}
import com.softwaremill.sttp.HttpURLConnectionBackend

// This is just for functional testing locally.
object StandaloneApp extends App {

  val stopDate = args.headOption.map(LocalDate.parse)

  private val value: Either[OverallFailure, Config] = Config(GetFromS3.fetchString)
  value match {
    case Left(msg) => println(s"Config failure: $msg")
    case Right(config) =>
      val processResult = Processor.processAllProducts(config, stopDate, HttpURLConnectionBackend())

      println(processResult.flatMap(_.holidayStopsToApply).size)

      processResult.flatMap(_.overallFailure) foreach { failure =>
        println(s"Overall failure: ${failure.reason}")
      }
      processResult.flatMap(_.holidayStopResults) foreach {
        case Left(failure) => println(s"Failed: ${failure.reason}")
        case Right(response) => println(s"Success: $response")
      }
  }
}
