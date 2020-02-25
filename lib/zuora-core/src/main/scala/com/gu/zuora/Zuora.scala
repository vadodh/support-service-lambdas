package com.gu.zuora

import com.gu.zuora.subscription._
import io.circe.generic.auto._
import sttp.client._
import sttp.client.circe._

object Zuora {
  def accessTokenGetResponse(
    config: ZuoraConfig,
    backend: SttpBackend[Identity, Nothing, NothingT]
  ): ZuoraApiResponse[AccessToken] = {
    implicit val b: SttpBackend[Identity, Nothing, NothingT] = backend
    basicRequest.post(uri"${config.baseUrl.stripSuffix("/v1")}/oauth/token")
      .body(
        "grant_type" -> "client_credentials",
        "client_id" -> s"${config.holidayStopProcessor.oauth.clientId}",
        "client_secret" -> s"${config.holidayStopProcessor.oauth.clientSecret}"
      )
      .response(asJson[AccessToken])
      .mapResponse(_.left.map(e => ZuoraApiFailure(e.getMessage)))
      .send()
      .body
  }

  def subscriptionGetResponse(config: ZuoraConfig, accessToken: AccessToken, backend: SttpBackend[Identity, Nothing, NothingT])(subscriptionName: SubscriptionName): ZuoraApiResponse[Subscription] = {
    implicit val b: SttpBackend[Identity, Nothing, NothingT] = backend
    basicRequest.get(uri"${config.baseUrl}/subscriptions/${subscriptionName.value}")
      .header("Authorization", s"Bearer ${accessToken.access_token}")
      .response(asJson[Subscription])
      .mapResponse(_.left.map(e => ZuoraApiFailure(e.getMessage)))
      .send()
      .body
  }

  def subscriptionUpdateResponse(config: ZuoraConfig, accessToken: AccessToken, backend: SttpBackend[Identity, Nothing, NothingT])(subscription: Subscription, update: SubscriptionUpdate): ZuoraApiResponse[Unit] = {
    implicit val b: SttpBackend[Identity, Nothing, NothingT] = backend
    val errMsg = (reason: String) => s"Failed to update subscription '${subscription.subscriptionNumber}' with $update. Reason: $reason"
    basicRequest.put(uri"${config.baseUrl}/subscriptions/${subscription.subscriptionNumber}")
      .header("Authorization", s"Bearer ${accessToken.access_token}")
      .body(update)
      .response(asJson[ZuoraStatusResponse])
      .mapResponse {
        case Left(e) => Left(ZuoraApiFailure(errMsg(e.getMessage)))
        case Right(status) =>
          if (status.success) Right(())
          else Left(ZuoraApiFailure(errMsg(status.reasons.map(_.mkString).getOrElse(""))))
      }
      .send()
      .body
  }

  def accountGetResponse(
    config: ZuoraConfig,
    accessToken: AccessToken,
    backend: SttpBackend[Identity, Nothing, NothingT]
  )(
    accountNumber: String
  ): ZuoraApiResponse[ZuoraAccount] = {
    implicit val b: SttpBackend[Identity, Nothing, NothingT] = backend
    basicRequest.get(uri"${config.baseUrl}/accounts/$accountNumber")
      .header("Authorization", s"Bearer ${accessToken.access_token}")
      .response(asJson[ZuoraAccount])
      .mapResponse(_.left.map(e => ZuoraApiFailure(e.getMessage)))
      .send()
      .body
  }

}
