package com.gu.zuora

import com.gu.zuora.subscription.{Subscription, SubscriptionName}
import zio.IO

trait Zuora2 {
  def zuora2: Zuora2.Service[Any]
}

object Zuora2 {
  trait Service[R] {

    //    def getSubscription(
    //      subscriptionName: SubscriptionName
    //    ): ZIO[R, ZuoraApiFailure, Subscription]
    def getSubscription(subscriptionName: SubscriptionName): IO[R, Subscription]

    //    def updateSubscription(
    //      subscription: Subscription,
    //      update: SubscriptionUpdate
    //    ): ZIO[R, ZuoraApiFailure, Unit]
    //
    //    def getAccount(accountNumber: String): ZIO[R, ZuoraApiFailure, ZuoraAccount]
  }

  //  object factory extends Service[Zuora2] {
  //
  //    def getSubscription(
  //      subscriptionName: SubscriptionName
  //    ): ZIO[Zuora2, ZuoraApiFailure, Subscription] =
  //      ZIO.accessM(_.zuora2.getSubscription(subscriptionName))
  //
  //    def updateSubscription(
  //      subscription: Subscription,
  //      update: SubscriptionUpdate
  //    ): ZIO[Zuora2, ZuoraApiFailure, Unit] =
  //      ZIO.accessM(_.zuora2.updateSubscription(subscription, update))
  //
  //    def getAccount(
  //      accountNumber: String
  //    ): ZIO[Zuora2, ZuoraApiFailure, ZuoraAccount] =
  //      ZIO.accessM(_.zuora2.getAccount(accountNumber))
  //  }
}
