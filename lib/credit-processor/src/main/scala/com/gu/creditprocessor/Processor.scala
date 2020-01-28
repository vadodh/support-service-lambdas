package com.gu.creditprocessor

import java.time.LocalDate

import cats.implicits._
import com.gu.fulfilmentdates.FulfilmentDatesFetcher
import com.gu.zuora.ZuoraProductTypes.ZuoraProductType
import com.gu.zuora.subscription._
import org.slf4j.LoggerFactory

object Processor {
  private val logger = LoggerFactory.getLogger(getClass)

  // TODO: could pass in a typeclass instead of a huge list of arguments here
  def processProduct[RequestType <: CreditRequest, ResultType <: ZuoraCreditAddResult](
    creditProduct: CreditProduct,
    getCreditRequestsFromSalesforce: (ZuoraProductType, List[LocalDate]) => SalesforceApiResponse[List[RequestType]],
    fulfilmentDatesFetcher: FulfilmentDatesFetcher,
    processOverrideDate: Option[LocalDate],
    productType: ZuoraProductType,
    getSubscription: SubscriptionName => ZuoraApiResponse[Subscription],
    getAccount: String => ZuoraApiResponse[ZuoraAccount],
    updateToApply: (CreditProduct, Subscription, ZuoraAccount, AffectedPublicationDate) => ZuoraApiResponse[SubscriptionUpdate],
    updateSubscription: (Subscription, SubscriptionUpdate) => ZuoraApiResponse[Unit],
    resultOfZuoraCreditAdd: (RequestType, RatePlanCharge) => ResultType,
    writeCreditResultsToSalesforce: List[ResultType] => SalesforceApiResponse[Unit]
  ): ProcessResult[ResultType] = {
    val creditRequestsFromSalesforce = for {
      datesToProcess <- getDatesToProcess(fulfilmentDatesFetcher, productType, processOverrideDate, LocalDate.now())
      _ = logger.info(s"Processing holiday stops for $productType for issue dates ${datesToProcess.mkString(", ")}")
      salesforceCreditRequests <- if (datesToProcess.isEmpty) Nil.asRight else getCreditRequestsFromSalesforce(productType, datesToProcess)
    } yield salesforceCreditRequests

    creditRequestsFromSalesforce match {
      case Left(sfReadError) =>
        ProcessResult(Nil, Nil, Nil, Some(OverallFailure(sfReadError.reason)))

      case Right(creditRequestsFromSalesforce) =>
        val creditRequests = creditRequestsFromSalesforce.distinct
        val alreadyActionedCredits = creditRequestsFromSalesforce.flatMap(_.chargeCode).distinct
        val allZuoraCreditResponses = creditRequests.map(
          addCreditToSubscription(
            creditProduct,
            getSubscription,
            getAccount,
            updateToApply,
            updateSubscription,
            resultOfZuoraCreditAdd
          )
        )
        val (failedZuoraResponses, successfulZuoraResponses) = allZuoraCreditResponses.separate
        val notAlreadyActionedCredits = successfulZuoraResponses.filterNot(v => alreadyActionedCredits.contains(v.chargeCode))
        val salesforceExportResult = writeCreditResultsToSalesforce(notAlreadyActionedCredits)
        ProcessResult(
          creditRequests,
          allZuoraCreditResponses,
          notAlreadyActionedCredits,
          OverallFailure(failedZuoraResponses, salesforceExportResult)
        )
    }
  }

  /**
   * This is the main business logic for adding a credit amendment to a subscription in Zuora
   */
  def addCreditToSubscription[RequestType <: CreditRequest, ResultType <: ZuoraCreditAddResult](
    creditProduct: CreditProduct,
    getSubscription: SubscriptionName => ZuoraApiResponse[Subscription],
    getAccount: String => ZuoraApiResponse[ZuoraAccount],
    updateToApply: (CreditProduct, Subscription, ZuoraAccount, AffectedPublicationDate) => ZuoraApiResponse[SubscriptionUpdate],
    updateSubscription: (Subscription, SubscriptionUpdate) => ZuoraApiResponse[Unit],
    result: (RequestType, RatePlanCharge) => ResultType
  )(request: RequestType): ZuoraApiResponse[ResultType] =
    for {
      subscription <- getSubscription(request.subscriptionName)
      account <- getAccount(subscription.accountNumber)
      _ <- if (subscription.status == "Cancelled") Left(ZuoraApiFailure(s"Cannot process cancelled subscription because Zuora does not allow amending cancelled subs (Code: 58730020). Apply manual refund ASAP! $request; ${subscription.subscriptionNumber};")) else Right(())
      subscriptionUpdate <- updateToApply(creditProduct, subscription, account, request.publicationDate)
      _ <- if (subscription.hasCreditAmendment(request)) Right(()) else updateSubscription(subscription, subscriptionUpdate)
      updatedSubscription <- getSubscription(request.subscriptionName)
      addedCharge <- updatedSubscription.ratePlanCharge(request).toRight(ZuoraApiFailure(s"Failed to write holiday stop to Zuora: $request"))
    } yield result(request, addedCharge)

  def getDatesToProcess(
    fulfilmentDatesFetcher: FulfilmentDatesFetcher,
    zuoraProductType: ZuoraProductType,
    processOverRideDate: Option[LocalDate],
    today: LocalDate
  ): Either[ZuoraApiFailure, List[LocalDate]] = {
    processOverRideDate
      .fold(
        fulfilmentDatesFetcher
          .getFulfilmentDates(zuoraProductType, today)
          .map(fulfilmentDates =>
            fulfilmentDates.values.flatMap(_.holidayStopProcessorTargetDate).toList)
          .leftMap(error => ZuoraApiFailure(s"Failed to fetch fulfilment dates: $error"))
      )(
          processOverRideDate => List(processOverRideDate).asRight
        )
  }
}