package com.gu.holiday_stops

import java.time.LocalDate

import com.gu.holiday_stops.subscription.{BillingPeriod, StoppedProduct, Subscription}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequest.HolidayStopRequest
import com.gu.salesforce.holiday_stops.{SalesforceHolidayStopRequest, SalesforceHolidayStopRequestsDetail}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.{HolidayStopRequestsDetailChargeCode, HolidayStopRequestsDetailChargePrice, StoppedPublicationDate, SubscriptionName}

final case class HolidayStopSubscriptionCancellationError(reason: String)

object HolidayStopSubscriptionCancellation {
  def apply(
    cancellationDate: LocalDate,
    holidayStopRequests: List[HolidayStopRequest],
    subscription: Subscription
  ): Either[ZuoraHolidayError, List[SalesforceHolidayStopRequestsDetail.HolidayStopRequestsDetail]] = {
    val lastDayOfCanceledSubscription = cancellationDate.minusDays(1)

    for {
      stoppedProduct <- StoppedProduct(subscription, StoppedPublicationDate(lastDayOfCanceledSubscription))

      allHolidayStopRequestDetails = holidayStopRequests
        .flatMap { holidayStopRequest =>
          holidayStopRequest
            .Holiday_Stop_Request_Detail__r
            .map(_.records)
            .getOrElse(Nil)
        }

      holidayStopsThatShouldBeRefunded = allHolidayStopRequestDetails
        .collect {
          case requestDetail if holidayStopShouldBeRefunded(
            requestDetail,
            cancellationDate,
            stoppedProduct.stoppedPublicationDateBillingPeriod
          ) =>
            val chargeCode = requestDetail
              .Charge_Code__c
              .getOrElse(HolidayStopRequestsDetailChargeCode("ManualRefund_Cancellation"))
            requestDetail.copy(
              Charge_Code__c = Some(chargeCode),
              Actual_Price__c = requestDetail.Estimated_Price__c
            )
        }
    } yield holidayStopsThatShouldBeRefunded
  }

  private def holidayStopShouldBeRefunded(
    requestDetail: SalesforceHolidayStopRequestsDetail.HolidayStopRequestsDetail,
    cancellationDate: LocalDate,
    lastBillingPeriodOfCanceledSubscription: BillingPeriod
  ) = {
    val stopDateIsBeforeOrEqualToCancelationDate =
      !requestDetail.Stopped_Publication_Date__c.value.isAfter(cancellationDate)

    val stopDateIsAfterOrEqualToStartOfLastBillingPeriodBeforeCancelation =
      !requestDetail.Stopped_Publication_Date__c.value.isBefore(lastBillingPeriodOfCanceledSubscription.startDate)

    stopDateIsAfterOrEqualToStartOfLastBillingPeriodBeforeCancelation && stopDateIsBeforeOrEqualToCancelationDate
  }
}
