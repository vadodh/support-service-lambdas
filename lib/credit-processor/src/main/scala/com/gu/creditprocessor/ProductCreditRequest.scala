package com.gu.creditprocessor

import java.time.LocalDate

import com.gu.fulfilmentdates.FulfilmentDatesFetcher
import com.gu.zuora.ZuoraProductTypes.ZuoraProductType
import com.gu.zuora.subscription._

trait ProductCreditRequest[Request <: CreditRequest, Result <: ZuoraCreditAddResult] {

  val fulfilmentDatesFetcher: FulfilmentDatesFetcher
  val processOverrideDate: Option[LocalDate]
  val productType: ZuoraProductType
  val creditProduct: CreditProduct

  def getCreditRequestsFromSalesforce(
    productType: ZuoraProductType,
    affectedDates: List[LocalDate]
  ): SalesforceApiResponse[List[Request]]

  def getSubscription(subscriptionName: SubscriptionName): ZuoraApiResponse[Subscription]

  def updateToApply(
    creditProduct: CreditProduct,
    subscription: Subscription,
    affectedDate: AffectedPublicationDate
  ): ZuoraApiResponse[SubscriptionUpdate]

  def updateSubscription(
    subscription: Subscription,
    update: SubscriptionUpdate
  ): ZuoraApiResponse[Unit]

  def resultOfZuoraCreditAdd(request: Request, ratePlanCharge: RatePlanCharge): Result

  def writeCreditResultsToSalesforce(results: List[Result]): SalesforceApiResponse[Unit]
}
