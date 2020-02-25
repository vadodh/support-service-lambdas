package com.gu.zuora

import com.gu.zuora.subscription.{Subscription, SubscriptionName, SubscriptionUpdate, ZuoraAccount, ZuoraApiFailure}
import com.softwaremill.sttp.circe.{asJson, _}
import com.softwaremill.sttp.sttp
import zio.{IO, Task, ZIO}
import com.gu.zuora.subscription._
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.zio.AsyncHttpClientZioBackend
import io.circe
import io.circe.generic.auto._

trait ZuoraLive extends Zuora2 {

  val configService: ZuoraConfiguration.Service[Any]

  def zuora2: Zuora2.Service[Any] = new Zuora2.Service[Any] {

    implicit val sttpBackend =
      AsyncHttpClientZioBackend()
    //  private implicit val sttpBackend = HttpURLConnectionBackend()

    private lazy val accessToken: IO[ZuoraApiFailure, AccessToken] = {
      for {
        config <- configService.zuoraConfig
        value1 <- sttp.post(uri"${config.baseUrl.stripSuffix("/v1")}/oauth/token")
          .body(
            "grant_type" -> "client_credentials",
            "client_id" -> s"${config.holidayStopProcessor.oauth.clientId}",
            "client_secret" -> s"${config.holidayStopProcessor.oauth.clientSecret}"
          ).response(asJson[AccessToken])
          .mapResponse(_.left.map(e => ZuoraApiFailure(e.message)))
          .send()
        either = value1.body.left.map(e => ZuoraApiFailure(e)).joinRight
        token <- ZIO.fromEither(either)
      } yield token
    }

    def getSubscription(subscriptionName: SubscriptionName): IO[ZuoraApiFailure, Subscription] = {
      for {
        config <- configService.zuoraConfig
        token <- accessToken.map(_.access_token)
        v <- sttp.get(uri"${config.baseUrl}/subscriptions/${subscriptionName.value}")
          .header("Authorization", s"Bearer $token")
          .response(asJson[Subscription])
          .mapResponse(_.left.map(e => ZuoraApiFailure(e.message)))
          .send()
        w = v
          .body.left.map(ZuoraApiFailure)
          .joinRight
        subscription <- ZIO.fromEither(w)
      } yield subscription
    }
  }
}
