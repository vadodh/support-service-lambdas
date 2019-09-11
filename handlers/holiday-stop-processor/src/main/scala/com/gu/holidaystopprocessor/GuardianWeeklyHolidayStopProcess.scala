package com.gu.holidaystopprocessor

import java.time.LocalDate

import com.gu.holiday_stops.{CreditCalculator, CurrentGuardianWeeklySubscription, ExtendedTerm, GuardianWeeklyHolidayStopConfig, HolidayCreditProduct, HolidayCreditUpdate, HolidayStop, OverallFailure, SalesforceHolidayWriteError, Subscription, ZuoraHolidayWriteError}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.{HolidayStopRequestsDetail, HolidayStopRequestsDetailChargeCode, HolidayStopRequestsDetailChargePrice, ProductName, StoppedPublicationDate, SubscriptionName}
import cats.implicits._
import com.gu.holiday_stops.ActionCalculator.{GuardianWeeklySuspensionConstants, suspensionConstantsByProduct}

object GuardianWeeklyHolidayStopProcess {
  def processHolidayStops(
    config: GuardianWeeklyHolidayStopConfig,
    getHolidayStopRequestsFromSalesforce: (ProductName, LocalDate) => Either[OverallFailure, List[HolidayStopRequestsDetail]],
    getSubscription: SubscriptionName => Either[ZuoraHolidayWriteError, Subscription],
    updateSubscription: (Subscription, HolidayCreditUpdate) => Either[ZuoraHolidayWriteError, Unit],
    writeHolidayStopsToSalesforce: List[HolidayStopResponse] => Either[SalesforceHolidayWriteError, Unit],
    processDateOverride: Option[LocalDate]
  ): ProcessResult = {
    getHolidayStopRequestsFromSalesforce(ProductName("Guardian Weekly"), calculateProcessDate(processDateOverride)) match {
      case Left(overallFailure) =>
        ProcessResult(overallFailure)

      case Right(holidayStopRequestsFromSalesforce) =>
        val holidayStops = holidayStopRequestsFromSalesforce.distinct.map(HolidayStop(_))
        val alreadyActionedHolidayStops = holidayStopRequestsFromSalesforce.flatMap(_.Charge_Code__c).distinct
        val allZuoraHolidayStopResponses =
          holidayStops.map(
            writeHolidayStopToZuora(
              config.holidayCreditProduct,
              config.productRatePlanIds,
              config.nForNProductRatePlanIds,
              getSubscription,
              updateSubscription
            )
          )
        val (failedZuoraResponses, successfulZuoraResponses) = allZuoraHolidayStopResponses.separate
        val notAlreadyActionedHolidays = successfulZuoraResponses.filterNot(v => alreadyActionedHolidayStops.contains(v.chargeCode))
        val salesforceExportResult = writeHolidayStopsToSalesforce(notAlreadyActionedHolidays)
        ProcessResult(
          holidayStops,
          allZuoraHolidayStopResponses,
          notAlreadyActionedHolidays,
          OverallFailure(failedZuoraResponses, salesforceExportResult)
        )
    }
  }

  private def calculateProcessDate(processDateOverride: Option[LocalDate]) = {
    processDateOverride.getOrElse(LocalDate.now.plusDays(GuardianWeeklySuspensionConstants.processorRunLeadTimeDays))
  }

  /**
   * This is the main business logic for writing holiday stop to Zuora
   */
  def writeHolidayStopToZuora(
    holidayCreditProduct: HolidayCreditProduct,
    guardianWeeklyProductRatePlanIds: List[String],
    gwNforNProductRatePlanIds: List[String],
    getSubscription: SubscriptionName => Either[ZuoraHolidayWriteError, Subscription],
    updateSubscription: (Subscription, HolidayCreditUpdate) => Either[ZuoraHolidayWriteError, Unit]
  )(stop: HolidayStop): Either[ZuoraHolidayWriteError, HolidayStopResponse] =
    for {
      subscription <- getSubscription(stop.subscriptionName)
      _ <- if (subscription.autoRenew) Right(()) else Left(ZuoraHolidayWriteError("Cannot currently process non-auto-renewing subscription"))
      currentGuardianWeeklySubscription <- CurrentGuardianWeeklySubscription(subscription, guardianWeeklyProductRatePlanIds, gwNforNProductRatePlanIds)
      nextInvoiceStartDate = NextBillingPeriodStartDate(currentGuardianWeeklySubscription, stop.stoppedPublicationDate)
      maybeExtendedTerm = ExtendedTerm(nextInvoiceStartDate, subscription)
      holidayCredit <- CreditCalculator.guardianWeeklyCredit(guardianWeeklyProductRatePlanIds, gwNforNProductRatePlanIds, stop.stoppedPublicationDate)(subscription)
      holidayCreditUpdate <- HolidayCreditUpdate(holidayCreditProduct, subscription, stop.stoppedPublicationDate, nextInvoiceStartDate, maybeExtendedTerm, holidayCredit)
      _ <- if (subscription.hasHolidayStop(stop)) Right(()) else updateSubscription(subscription, holidayCreditUpdate)
      updatedSubscription <- getSubscription(stop.subscriptionName)
      addedCharge <- updatedSubscription.ratePlanCharge(stop).toRight(ZuoraHolidayWriteError("Failed to add charge to subscription"))
    } yield {
      HolidayStopResponse(
        stop.requestId,
        stop.subscriptionName,
        stop.productName,
        HolidayStopRequestsDetailChargeCode(addedCharge.number),
        stop.estimatedCharge,
        HolidayStopRequestsDetailChargePrice(addedCharge.price),
        StoppedPublicationDate(addedCharge.HolidayStart__c.getOrElse(LocalDate.MIN))
      )
    }
}
