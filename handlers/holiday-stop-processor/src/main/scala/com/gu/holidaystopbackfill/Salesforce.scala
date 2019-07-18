package com.gu.holidaystopbackfill

import java.time.LocalDate

import com.gu.effects.RawEffects
import com.gu.salesforce.SalesforceAuthenticate.SFAuthConfig
import com.gu.salesforce.SalesforceClient
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequest
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequest.{CreateHolidayStopRequest, HolidayStopRequest, NewHolidayStopRequest}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.{HolidayStopRequestsDetail, ProductName}
import com.gu.util.Time
import com.gu.util.resthttp.JsonHttp
import scalaz.{-\/, \/-}

object Salesforce {

  def holidayStopRequests(sfCredentials: SFAuthConfig)(productNamePrefix: String, thresholdDate: LocalDate): Either[SalesforceFetchFailure, Seq[HolidayStopRequest]] =
    SalesforceClient(RawEffects.response, sfCredentials).value.flatMap { sfAuth =>
      val sfGet = sfAuth.wrapWith(JsonHttp.getWithParams)
      val fetchOp = SalesforceHolidayStopRequest.LookupByDateAndProductNamePrefix(sfGet)
      fetchOp(Time.toJodaDate(thresholdDate), ProductName(productNamePrefix))
    }.toDisjunction match {
      case -\/(failure) => Left(SalesforceFetchFailure(failure.toString))
      case \/-(requests) => Right(requests)
    }

  def holidayStopRequestsByProductAndDateRange(sfCredentials: SFAuthConfig)(productNamePrefix: ProductName, start: LocalDate, end: LocalDate): Either[SalesforceFetchFailure, Seq[HolidayStopRequest]] =
    SalesforceClient(RawEffects.response, sfCredentials).value.flatMap { sfAuth =>
      val sfGet = sfAuth.wrapWith(JsonHttp.getWithParams)
      val fetchOp = SalesforceHolidayStopRequest.LookupByDateRangeAndProductNamePrefix(sfGet)
      fetchOp(Time.toJodaDate(start), Time.toJodaDate(end), productNamePrefix)
    }.toDisjunction match {
      case -\/(failure) => Left(SalesforceFetchFailure(failure.toString))
      case \/-(requests) => Right(requests)
    }

  def holidayStopRequestDetails(sfCredentials: SFAuthConfig)(productNamePrefix: ProductName, startThreshold: LocalDate, endThreshold: LocalDate): Either[SalesforceFetchFailure, Seq[HolidayStopRequestsDetail]] = ???
  //    SalesforceClient(RawEffects.response, sfCredentials).value.flatMap { sfAuth =>
  //      val sfGet = sfAuth.wrapWith(JsonHttp.getWithParams)
  //      val fetchOp = SalesforceHolidayStopRequestsDetail.LookupByProductNamePrefixAndDateRange(sfGet)
  //      fetchOp(productNamePrefix, startThreshold, endThreshold)
  //    }.toDisjunction match {
  //      case -\/(failure) => Left(SalesforceFetchFailure(failure.toString))
  //      case \/-(details) => Right(details)
  //    }

  def holidayStopUpdateResponse(sfCredentials: SFAuthConfig)(zuoraRefs: Seq[HolidayStopRequestsDetail]): Either[SalesforceUpdateFailure, Unit] = ???
  //    SalesforceClient(RawEffects.response, sfCredentials).value.map { sfAuth =>
  //      val sfGet = sfAuth.wrapWith(JsonHttp.post)
  //      val sendOp = CreatePendingSalesforceHolidayStopRequestsDetail(sfGet)
  //      zuoraRefs.map(sendOp).find(_.isFailure)
  //    }.toDisjunction match {
  //      case -\/(failure) => Left(SalesforceUpdateFailure(failure.toString))
  //      case _ => Right(())
  //    }

  def holidayStopCreateResponse(sfCredentials: SFAuthConfig)(requests: Seq[NewHolidayStopRequest]): Either[SalesforceUpdateFailure, Unit] =
    SalesforceClient(RawEffects.response, sfCredentials).value.map { sfAuth =>
      val sfGet = sfAuth.wrapWith(JsonHttp.post)
      val createOp = CreateHolidayStopRequest(sfGet)
      requests.map(createOp).find(_.isFailure)
    }.toDisjunction match {
      case -\/(failure) => Left(SalesforceUpdateFailure(failure.toString))
      case _ => Right(())
    }
}
