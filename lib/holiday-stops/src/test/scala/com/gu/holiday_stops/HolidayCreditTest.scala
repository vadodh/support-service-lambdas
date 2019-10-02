package com.gu.holiday_stops

import java.time.LocalDate

import com.gu.holiday_stops.subscription.{GuardianWeeklySubscription, RatePlan}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.StoppedPublicationDate
import org.scalatest.{EitherValues, FlatSpec, Matchers}

class HolidayCreditTest extends FlatSpec with Matchers with EitherValues {

  val stoppedPublicationDate = StoppedPublicationDate(LocalDate.parse("2019-09-01").minusDays(1))

  "HolidayCredit" should "be correct for a quarterly billing period" in {
    val charge = Fixtures.mkRatePlanCharge(price = 30, billingPeriod = "Quarter")
    val ratePlans = List(RatePlan("Guardian Weekly - Domestic", List(charge), "", ""))
    val subscription = Fixtures.mkSubscription().copy(ratePlans = ratePlans)
    val currentGuardianWeeklySubscription = GuardianWeeklySubscription(subscription, stoppedPublicationDate)
    val credit = currentGuardianWeeklySubscription.right.value.credit
    credit shouldBe -2.31
  }

  it should "be correct for another quarterly billing period" in {
    val charge = Fixtures.mkRatePlanCharge(price = 37.5, billingPeriod = "Quarter")
    val ratePlans = List(RatePlan("Guardian Weekly - Domestic", List(charge), "", ""))
    val subscription = Fixtures.mkSubscription().copy(ratePlans = ratePlans)
    val currentGuardianWeeklySubscription = GuardianWeeklySubscription(subscription, stoppedPublicationDate)
    val credit = currentGuardianWeeklySubscription.right.value.credit
    credit shouldBe -2.89
  }

  it should "be correct for an annual billing period" in {
    val charge = Fixtures.mkRatePlanCharge(price = 120, billingPeriod = "Annual")
    val ratePlans = List(RatePlan("Guardian Weekly - Domestic", List(charge), "", ""))
    val subscription = Fixtures.mkSubscription().copy(ratePlans = ratePlans)
    val currentGuardianWeeklySubscription = GuardianWeeklySubscription(subscription, stoppedPublicationDate)
    val credit = currentGuardianWeeklySubscription.right.value.credit
    credit shouldBe -2.31
  }
}
