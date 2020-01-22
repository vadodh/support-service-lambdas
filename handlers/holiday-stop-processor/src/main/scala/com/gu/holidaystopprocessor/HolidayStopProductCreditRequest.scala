package com.gu.holidaystopprocessor

import java.time.LocalDate

import com.gu.creditprocessor.ProductCreditRequest
import com.gu.fulfilmentdates.FulfilmentDatesFetcher
import com.gu.holiday_stops.{AccessToken, Config, Zuora}
import com.gu.salesforce.holiday_stops.SalesforceHolidayStopRequestsDetail.HolidayStopRequestsDetail
import com.gu.util.config.Stage
import com.gu.zuora.ZuoraProductTypes
import com.gu.zuora.ZuoraProductTypes.ZuoraProductType
import com.gu.zuora.subscription._
import com.softwaremill.sttp.{Id, SttpBackend}

case class HolidayStopProductCreditRequest(
  stage: Stage,
  config: Config,
  zuoraAccessToken: AccessToken,
  backend: SttpBackend[Id, Nothing],
  productType: ZuoraProductType,
  fulfilmentDatesFetcher: FulfilmentDatesFetcher,
  processOverrideDate: Option[LocalDate]
) extends ProductCreditRequest[HolidayStopRequestsDetail, ZuoraHolidayCreditAddResult] {

  val creditProduct: CreditProduct = HolidayCreditProduct.forStage(stage)

  def getCreditRequestsFromSalesforce(
    productType: ZuoraProductTypes.ZuoraProductType,
    affectedDates: List[LocalDate]
  ): SalesforceApiResponse[List[HolidayStopRequestsDetail]] =
    Salesforce.holidayStopRequests(config.sfConfig)(productType, affectedDates)

  def getSubscription(
    subscriptionName: SubscriptionName
  ): ZuoraApiResponse[Subscription] =
    Zuora.subscriptionGetResponse(config, zuoraAccessToken, backend)(subscriptionName)

  def updateToApply(
    creditProduct: CreditProduct,
    subscription: Subscription,
    affectedDate: AffectedPublicationDate
  ): ZuoraApiResponse[SubscriptionUpdate] =
    SubscriptionUpdate.forHolidayStop(creditProduct, subscription, affectedDate)

  def updateSubscription(
    subscription: Subscription,
    update: SubscriptionUpdate
  ): ZuoraApiResponse[Unit] =
    Zuora.subscriptionUpdateResponse(config, zuoraAccessToken, backend)(subscription, update)

  def resultOfZuoraCreditAdd(
    request: HolidayStopRequestsDetail,
    ratePlanCharge: RatePlanCharge
  ): ZuoraHolidayCreditAddResult =
    ZuoraHolidayCreditAddResult.apply(request, ratePlanCharge)

  def writeCreditResultsToSalesforce(
    results: List[ZuoraHolidayCreditAddResult]
  ): SalesforceApiResponse[Unit] =
    Salesforce.holidayStopUpdateResponse(config.sfConfig)(results)
}
