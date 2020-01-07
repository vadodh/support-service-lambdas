package com.gu.holiday_stops

import com.gu.effects.GetFromS3
import zio.{IO, ZIO}

trait Configuration {
  val configuration: Configuration.Service
}

object Configuration {

  trait Service {
    val config: IO[OverallFailure, Config]
  }

  object > {
    val config: ZIO[Configuration, OverallFailure, Config] =
      ZIO.accessM(_.configuration.config)
  }
}

trait ConfigLive extends Configuration {
  val configuration: Configuration.Service = new Configuration.Service {
    val config: IO[OverallFailure, Config] =
      IO.fromEither[OverallFailure, Config](Config(GetFromS3.fetchString))
  }
}

object ConfigLive extends ConfigLive
