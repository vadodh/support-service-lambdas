package com.gu.holiday_stops

import com.gu.holiday_stops.subscription.{HolidayCreditUpdate, Subscription}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.SubscriptionName
import zio.blocking.Blocking
import zio.{DefaultRuntime, Exit}

object ZuoraZioAdapter {

  private val runtime = new DefaultRuntime {}
  private lazy val envImpl = new ConfigLive with Blocking.Live {}

  private def toResponse[A](result: Exit[HolidayError, A]): ZuoraHolidayResponse[A] =
    result.fold(e => Left(ZuoraHolidayError(e.prettyPrint)), Right(_))

  def getSubscription(subscriptionName: SubscriptionName): ZuoraHolidayResponse[Subscription] = {
    val getOp = ZuoraLive.zuora.getSubscription(subscriptionName).provide(envImpl)
    toResponse(runtime.unsafeRunSync(getOp))
  }

  def updateSubscription(
    sub: Subscription,
    update: HolidayCreditUpdate
  ): ZuoraHolidayResponse[Unit] = {
    val updateOp = ZuoraLive.zuora.updateSubscription(sub, update).provide(envImpl)
    toResponse(runtime.unsafeRunSync(updateOp))
  }
}
