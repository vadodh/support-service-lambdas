package com.gu.GuardianWeeklyHolidayStopProcessor

import java.time.LocalDate

import cats.implicits._
import com.gu.holiday_stops.Fixtures._
import com.gu.holiday_stops._
import com.gu.holiday_stops.subscription.{HolidayCreditUpdate, Subscription}
import com.gu.holidaystopprocessor.{ZuoraHolidayWriteResult, Processor}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.{HolidayStopRequestsDetail, SubscriptionName}
import org.scalatest._

/**
 * Make sure short-circuiting does not happen.
 */
class ProcessorErrorHandlingSpec extends FlatSpec with Matchers with OptionValues {
  val holidayStopRequestsFromSalesforce: SalesforceHolidayResponse[List[HolidayStopRequestsDetail]] = {
    Right(List(
      mkHolidayStopRequestDetailsFromHolidayStopRequest(mkHolidayStopRequest("R1", LocalDate.of(2019, 8, 2), SubscriptionName("A-S1")), "C1"),
      mkHolidayStopRequestDetailsFromHolidayStopRequest(mkHolidayStopRequest("R2", LocalDate.of(2019, 9, 1), SubscriptionName("A-S2")), "C3"),
      mkHolidayStopRequestDetailsFromHolidayStopRequest(mkHolidayStopRequest("R3", LocalDate.of(2019, 8, 9), SubscriptionName("A-S3")), "C4")
    ))
  }

  val subscription: Subscription = mkSubscriptionWithHolidayStops()

  val updateSubscription: (Subscription, HolidayCreditUpdate) => Either[ZuoraHolidayError, Unit] = {
    case _ => Right(())
  }

  "Error handling" should "not short-circuit if some writes to Zuora fail (but others succeed), and Salesforce write succeeds" in {
    val getSubscription: SubscriptionName => Either[ZuoraHolidayError, Subscription] = {
      case subName if subName.value == "A-S1" => Right(subscription)
      case subName if subName.value == "A-S2" => Left(ZuoraHolidayError("zuora boom")) // NOTE: this line is key to the test
      case subName if subName.value == "A-S3" => Right(subscription)
    }

    val writeHolidayStopsToSalesforce: List[ZuoraHolidayWriteResult] => Either[SalesforceHolidayError, Unit] = {
      case _ => Right(())
    }

    val result = Processor.processProduct(
      Fixtures.config,
      holidayStopRequestsFromSalesforce,
      getSubscription,
      updateSubscription,
      writeHolidayStopsToSalesforce
    )

    val (failedZuoraResponses, successfulZuoraResponses) = result.holidayStopResults.separate
    failedZuoraResponses.size shouldBe 1
    successfulZuoraResponses.size shouldBe 2
    (failedZuoraResponses.size + successfulZuoraResponses.size) shouldBe 3
    result.overallFailure.value shouldBe (OverallFailure("zuora boom"))
  }

  it should "not short-circuit if all Zuora writes succeeds, and Salesforce write fails" in {
    val getSubscription: SubscriptionName => Either[ZuoraHolidayError, Subscription] = {
      case subName if subName.value == "A-S1" => Right(subscription)
      case subName if subName.value == "A-S2" => Right(subscription)
      case subName if subName.value == "A-S3" => Right(subscription)
    }

    val writeHolidayStopsToSalesforce: List[ZuoraHolidayWriteResult] => Either[SalesforceHolidayError, Unit] = {
      case _ => Left(SalesforceHolidayError("salesforce boom")) // NOTE: this line is key to the test
    }

    val result = Processor.processProduct(
      Fixtures.config,
      holidayStopRequestsFromSalesforce,
      getSubscription,
      updateSubscription,
      writeHolidayStopsToSalesforce
    )

    val (failedZuoraResponses, successfulZuoraResponses) = result.holidayStopResults.separate
    failedZuoraResponses.size shouldBe 0
    successfulZuoraResponses.size shouldBe 3
    (failedZuoraResponses.size + successfulZuoraResponses.size) shouldBe 3
    result.overallFailure.value shouldBe (OverallFailure("salesforce boom"))

  }

  it should "collect all Zuora failures" in {
    val getSubscription: SubscriptionName => Either[ZuoraHolidayError, Subscription] = {
      case subName if subName.value == "A-S1" => Left(ZuoraHolidayError("zuora boom 1"))
      case subName if subName.value == "A-S2" => Left(ZuoraHolidayError("zuora boom 2"))
      case subName if subName.value == "A-S3" => Left(ZuoraHolidayError("zuora boom 3"))
    }

    val writeHolidayStopsToSalesforce: List[ZuoraHolidayWriteResult] => Either[SalesforceHolidayError, Unit] = {
      case _ => Right(())
    }

    val result = Processor.processProduct(
      Fixtures.config,
      holidayStopRequestsFromSalesforce,
      getSubscription,
      updateSubscription,
      writeHolidayStopsToSalesforce
    )

    val (failedZuoraResponses, successfulZuoraResponses) = result.holidayStopResults.separate
    failedZuoraResponses.size shouldBe 3
    successfulZuoraResponses.size shouldBe 0
    (failedZuoraResponses.size + successfulZuoraResponses.size) shouldBe 3
    result.overallFailure.value shouldBe (OverallFailure("zuora boom 1"))
  }

  it should "be None if all Zuora writes succeed and Salesforce write succeeds" in {
    val getSubscription: SubscriptionName => Either[ZuoraHolidayError, Subscription] = {
      case subName if subName.value == "A-S1" => Right(subscription)
      case subName if subName.value == "A-S2" => Right(subscription)
      case subName if subName.value == "A-S3" => Right(subscription)
    }

    val writeHolidayStopsToSalesforce: List[ZuoraHolidayWriteResult] => Either[SalesforceHolidayError, Unit] = {
      case _ => Right(())
    }

    val result = Processor.processProduct(
      Fixtures.config,
      holidayStopRequestsFromSalesforce,
      getSubscription,
      updateSubscription,
      writeHolidayStopsToSalesforce
    )

    val (failedZuoraResponses, successfulZuoraResponses) = result.holidayStopResults.separate
    failedZuoraResponses.size shouldBe 0
    successfulZuoraResponses.size shouldBe 3
    (failedZuoraResponses.size + successfulZuoraResponses.size) shouldBe 3
    result.overallFailure should be(None)
  }
}