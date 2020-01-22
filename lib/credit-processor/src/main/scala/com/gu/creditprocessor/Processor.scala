package com.gu.creditprocessor

import java.time.LocalDate

import cats.implicits._
import com.gu.fulfilmentdates.FulfilmentDatesFetcher
import com.gu.zuora.ZuoraProductTypes.ZuoraProductType
import com.gu.zuora.subscription._
import org.slf4j.LoggerFactory

object Processor {
  private val logger = LoggerFactory.getLogger(getClass)

  def processProduct[Request <: CreditRequest, Result <: ZuoraCreditAddResult](p: ProductCreditRequest[Request, Result]): ProcessResult[Result] = {
    val creditRequestsFromSalesforce = for {
      datesToProcess <- getDatesToProcess(p, LocalDate.now())
      _ = logger.info(s"Processing holiday stops for ${p.productType} for issue dates ${datesToProcess.mkString(", ")}")
      salesforceCreditRequests <- if (datesToProcess.isEmpty) Nil.asRight else p.getCreditRequestsFromSalesforce(p.productType, datesToProcess)
    } yield salesforceCreditRequests

    creditRequestsFromSalesforce match {
      case Left(sfReadError) =>
        ProcessResult(Nil, Nil, Nil, Some(OverallFailure(sfReadError.reason)))

      case Right(creditRequestsFromSalesforce) =>
        val creditRequests = creditRequestsFromSalesforce.distinct
        val alreadyActionedCredits = creditRequestsFromSalesforce.flatMap(_.chargeCode).distinct
        val allZuoraCreditResponses = creditRequests.map(addCreditToSubscription(p))
        val (failedZuoraResponses, successfulZuoraResponses) = allZuoraCreditResponses.separate
        val notAlreadyActionedCredits = successfulZuoraResponses.filterNot(v => alreadyActionedCredits.contains(v.chargeCode))
        val salesforceExportResult = p.writeCreditResultsToSalesforce(notAlreadyActionedCredits)
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
  def addCreditToSubscription[Request <: CreditRequest, Result <: ZuoraCreditAddResult](p: ProductCreditRequest[Request, Result])(request: Request): ZuoraApiResponse[Result] =
    for {
      subscription <- p.getSubscription(request.subscriptionName)
      _ <- if (subscription.status == "Cancelled") Left(ZuoraApiFailure(s"Cannot process cancelled subscription because Zuora does not allow amending cancelled subs (Code: 58730020). Apply manual refund ASAP! $request; ${subscription.subscriptionNumber};")) else Right(())
      subscriptionUpdate <- p.updateToApply(p.creditProduct, subscription, request.publicationDate)
      _ <- if (subscription.hasCreditAmendment(request)) Right(()) else p.updateSubscription(subscription, subscriptionUpdate)
      updatedSubscription <- p.getSubscription(request.subscriptionName)
      addedCharge <- updatedSubscription.ratePlanCharge(request).toRight(ZuoraApiFailure(s"Failed to write holiday stop to Zuora: $request"))
    } yield p.resultOfZuoraCreditAdd(request, addedCharge)

  // TODO: extract this out
  def getDatesToProcess[Request <: CreditRequest, Result <: ZuoraCreditAddResult](
    p: ProductCreditRequest[Request, Result],
    today: LocalDate
  ): Either[ZuoraApiFailure, List[LocalDate]] =
    p.processOverrideDate
      .fold(
        p.fulfilmentDatesFetcher
          .getFulfilmentDates(p.productType, today)
          .map(fulfilmentDates =>
            fulfilmentDates.values.flatMap(x => x.holidayStopProcessorTargetDate).toList)
          .leftMap(error => ZuoraApiFailure(s"Failed to fetch fulfilment dates: $error"))
      )(
          date => List(date).asRight
        )
}
