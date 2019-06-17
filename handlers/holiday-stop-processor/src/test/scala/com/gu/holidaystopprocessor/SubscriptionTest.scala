package com.gu.holidaystopprocessor

import java.time.LocalDate

import org.scalatest.{FlatSpec, Matchers, OptionValues}

class SubscriptionTest extends FlatSpec with Matchers with OptionValues {

  "ratePlanCharge" should "give ratePlanCharge corresponding to holiday stop" in {
    val subscription = Fixtures.mkSubscriptionWithHolidayStops()
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 9))
    subscription.ratePlanCharge(stop).value shouldBe RatePlanCharge(
      name = "Holiday Credit",
      number = "C2",
      price = -3.27,
      billingPeriod = None,
      effectiveStartDate = LocalDate.of(2019, 9, 7),
      chargedThroughDate = None,
      HolidayStart__c = Some(LocalDate.of(2019, 8, 9)),
      HolidayEnd__c = Some(LocalDate.of(2019, 8, 9))
    )
  }

  it should "give another ratePlanCharge corresponding to another holiday stop" in {
    val subscription = Fixtures.mkSubscriptionWithHolidayStops()
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 2))
    subscription.ratePlanCharge(stop).value shouldBe RatePlanCharge(
      name = "Holiday Credit",
      number = "C3",
      price = -5.81,
      billingPeriod = None,
      effectiveStartDate = LocalDate.of(2019, 9, 7),
      chargedThroughDate = None,
      HolidayStart__c = Some(LocalDate.of(2019, 8, 2)),
      HolidayEnd__c = Some(LocalDate.of(2019, 8, 2))
    )
  }

  it should "give no ratePlanCharge when none correspond to holiday stop" in {
    val subscription = Fixtures.mkSubscriptionWithHolidayStops()
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 23))
    subscription.ratePlanCharge(stop) shouldBe None
  }

  it should "give no ratePlanCharge when subscription has no holiday stops applied" in {
    val subscription = Fixtures.mkSubscription(
      termEndDate = LocalDate.of(2019, 1, 1),
      price = 123,
      billingPeriod = "Quarter",
      chargedThroughDate = None
    )
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 23))
    subscription.ratePlanCharge(stop) shouldBe None
  }

  it should "give no RatePlanCharge when dates correspond but it's not for a holiday credit" in {
    val subscription = Fixtures.mkSubscriptionWithHolidayStops()
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 19))
    subscription.ratePlanCharge(stop) shouldBe None
  }

  it should "give no RatePlanCharge when dates correspond but it's not for a discount plan" in {
    val subscription = Fixtures.mkSubscriptionWithHolidayStops()
    val stop = Fixtures.mkHolidayStop(LocalDate.of(2019, 8, 11))
    subscription.ratePlanCharge(stop) shouldBe None
  }
}