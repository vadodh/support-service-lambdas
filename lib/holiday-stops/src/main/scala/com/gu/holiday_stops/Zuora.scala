package com.gu.holiday_stops

import com.gu.holiday_stops.subscription.{HolidayCreditUpdate, Subscription}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.SubscriptionName
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._
import io.circe.generic.auto._
import zio.ZIO
import zio.blocking._

trait Zuora {
  val zuora: Zuora.Service
}

object Zuora {

  trait Service {

    def getSubscription(
      subscriptionName: SubscriptionName
    ): ZIO[Configuration with Blocking, HolidayError, Subscription]

    def updateSubscription(
      subscription: Subscription,
      update: HolidayCreditUpdate
    ): ZIO[Configuration with Blocking, HolidayError, Unit]
  }

  object > {

    def getSubscription(
      subscriptionName: SubscriptionName
    ): ZIO[Zuora with Configuration with Blocking, HolidayError, Subscription] =
      ZIO.accessM(_.zuora.getSubscription(subscriptionName))

    def updateSubscription(
      subscription: Subscription,
      update: HolidayCreditUpdate
    ): ZIO[Zuora with Configuration with Blocking, HolidayError, Unit] =
      ZIO.accessM(_.zuora.updateSubscription(subscription, update))
  }
}

trait ZuoraLive extends Zuora {

  val zuora: Zuora.Service = new Zuora.Service {

    private implicit val b: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    private lazy val token: ZIO[Configuration with Blocking, HolidayError, AccessToken] =
      for {
        config <- Configuration.>.config
        accessToken <- blocking(ZIO.fromEither[ZuoraHolidayError, AccessToken](
          sttp.post(uri"${config.zuoraConfig.baseUrl.stripSuffix("/v1")}/oauth/token")
            .body(
              "grant_type" -> "client_credentials",
              "client_id" -> config.zuoraConfig.holidayStopProcessor.oauth.clientId,
              "client_secret" -> config.zuoraConfig.holidayStopProcessor.oauth.clientSecret
            )
            .response(asJson[AccessToken])
            .mapResponse(_.left.map(e => ZuoraHolidayError(e.message)))
            .send()
            .body
            .left
            .map(e => ZuoraHolidayError(e))
            .joinRight
        ))
      } yield accessToken

    def getSubscription(
      subscriptionName: SubscriptionName
    ): ZIO[Configuration with Blocking, HolidayError, Subscription] =
      for {
        config <- Configuration.>.config
        accessToken <- token
        subscription <- blocking(ZIO.fromEither[ZuoraHolidayError, Subscription](
          sttp.get(uri"${config.zuoraConfig.baseUrl}/subscriptions/${subscriptionName.value}")
            .header("Authorization", s"Bearer ${accessToken.access_token}")
            .response(asJson[Subscription])
            .mapResponse(_.left.map(e => ZuoraHolidayError(e.message)))
            .send()
            .body
            .left
            .map(ZuoraHolidayError)
            .joinRight
        ))
      } yield subscription

    def updateSubscription(
      subscription: Subscription,
      update: HolidayCreditUpdate
    ): ZIO[Configuration with Blocking, HolidayError, Unit] = {
      def errMsg(reason: String) =
        s"Failed to update subscription '${subscription.subscriptionNumber}' with $update. Reason: $reason"
      for {
        config <- Configuration.>.config
        accessToken <- token
        _ <- blocking(ZIO.fromEither[ZuoraHolidayError, Unit](
          sttp.put(uri"${config.zuoraConfig.baseUrl}/subscriptions/${subscription.subscriptionNumber}")
            .header("Authorization", s"Bearer ${accessToken.access_token}")
            .body(update)
            .response(asJson[ZuoraStatusResponse])
            .mapResponse {
              case Left(e) => Left(ZuoraHolidayError(errMsg(e.message)))
              case Right(status) =>
                if (status.success) Right(())
                else Left(ZuoraHolidayError(errMsg(status.reasons.map(_.mkString).getOrElse(""))))
            }
            .send()
            .body
            .left
            .map(reason => ZuoraHolidayError(errMsg(reason)))
            .joinRight
        ))
      } yield ()
    }
  }
}

object ZuoraLive extends ZuoraLive
