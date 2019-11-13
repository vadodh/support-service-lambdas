package com.gu.holiday_stops

import java.time.LocalDate

import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.{HolidayStopRequestsDetailChargeCode, HolidayStopRequestsDetailChargePrice}
import org.scalatest.{FlatSpec, Inside, Matchers}

class HolidayStopSubscriptionCancellationTest extends FlatSpec with Matchers {
  "HolidayStopSubscriptionCancellationTest" should "return holiday stops after start of last billing period but before cancellation date" in {
    val estimatedPrice = 1.23

    val startOfBillingPeriodBeforeCancellation = LocalDate.of(2019, 11, 15);
    val lastDayOfBillingPeriodBeforeCancellation = LocalDate.of(2020, 2, 14);
    val cancellationDate = lastDayOfBillingPeriodBeforeCancellation.minusDays(1)

    val beforeFinalBillingPeriod = testDetail(
      startOfBillingPeriodBeforeCancellation.minusDays(1),
      Some("ChargeCode-1111"),
      estimatedPrice
    )
    val cancelableDetail1 = testDetail(startOfBillingPeriodBeforeCancellation, None, estimatedPrice)
    val cancelableDetail2 = testDetail(cancellationDate, None, estimatedPrice)
    val allReadyProcessedDetail = testDetail(cancellationDate, Some("ChargeCode-1111"), estimatedPrice)
    val afterCancellationDateDetail = testDetail(cancellationDate.plusDays(1), None, estimatedPrice)

    val holidayStopRequests = List(
      Fixtures.mkHolidayStopRequest(
        "id",
        requestDetail = List(
          beforeFinalBillingPeriod,
          cancelableDetail1,
          cancelableDetail2,
          afterCancellationDateDetail,
          allReadyProcessedDetail
        )
      )
    )

    val subscription = Fixtures.subscriptionFromJson("GuardianWeeklyWith6For6.json")

    Inside.inside(HolidayStopSubscriptionCancellation(cancellationDate, holidayStopRequests, subscription)) {
      case Right(requestsDetails) =>
        requestsDetails should contain only (
          cancelableDetail1.copy(
            Actual_Price__c = Some(HolidayStopRequestsDetailChargePrice(estimatedPrice)),
            Charge_Code__c = Some(HolidayStopRequestsDetailChargeCode("ManualRefund_Cancellation"))
          ),
          cancelableDetail2.copy(
            Actual_Price__c = Some(HolidayStopRequestsDetailChargePrice(estimatedPrice)),
            Charge_Code__c = Some(HolidayStopRequestsDetailChargeCode("ManualRefund_Cancellation"))
          ),
          allReadyProcessedDetail.copy(
            Actual_Price__c = Some(HolidayStopRequestsDetailChargePrice(estimatedPrice))
          )
        )
    }
  }

  private def testDetail(date: LocalDate, chargeCode: Option[String], estimatedPrice: Double) = {
    val cancelableDetail1 = Fixtures.mkHolidayStopRequestDetails(
      estimatedPrice = Some(estimatedPrice),
      actualPrice = None,
      chargeCode = chargeCode,
      stopDate = date
    )
    cancelableDetail1
  }
}
