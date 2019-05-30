package com.gu.holidaystopprocessor

import java.time.LocalDate

import org.scalacheck.Prop.forAll
import org.scalacheck._
import org.scalatest.OptionValues

object HolidayCreditSpec extends Properties("HolidayCredit") with OptionValues {

  private val subscriptionGen = for {
    price <- Gen.choose(0.01, 10000)
    billingPeriod <- Gen.oneOf(Seq("Month", "Quarter", "Annual"))
  } yield Fixtures.mkSubscription(
    termEndDate = LocalDate.of(2019, 1, 1),
    price,
    billingPeriod,
    effectiveEndDate = LocalDate.of(2020, 1, 1)
  )

  property("should never be positive") = forAll(subscriptionGen) { subscription: Subscription =>
    HolidayCredit(subscription) <= 0
  }

  property("should never be overwhelmingly negative") = forAll(subscriptionGen) { subscription: Subscription =>
    HolidayCredit(subscription) > -subscription.originalRatePlanCharge.value.price
  }
}