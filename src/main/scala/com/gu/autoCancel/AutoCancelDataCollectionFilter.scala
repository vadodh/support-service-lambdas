package com.gu.autoCancel

import java.time.LocalDate

import com.gu.autoCancel.AutoCancel.AutoCancelRequest
import com.gu.util.Logging
import com.gu.util.apigateway.ApiGatewayResponse.noActionRequired
import com.gu.util.reader.Types.ApiGatewayOp._
import com.gu.util.reader.Types._
import com.gu.util.resthttp.Types.ClientFailableOp
import com.gu.util.zuora.ZuoraGetAccountSummary.{AccountSummary, Invoice, SubscriptionId}
import com.gu.stripeCustomerSourceUpdated.TypeConvert._

object AutoCancelDataCollectionFilter extends Logging {

  def apply(
    now: LocalDate,
    getAccountSummary: String => ClientFailableOp[AccountSummary]
  )(autoCancelCallout: AutoCancelCallout): ApiGatewayOp[AutoCancelRequest] = {
    val accountId = autoCancelCallout.accountId
    for {
      accountSummary <- getAccountSummary(accountId).toApiGatewayOp("getAccountSummary").withLogging("getAccountSummary")
      subToCancel <- getSubscriptionToCancel(accountSummary).withLogging("getSubscriptionToCancel")
      cancellationDate <- getCancellationDateFromInvoices(accountSummary, now).withLogging("getCancellationDateFromInvoices")
    } yield AutoCancelRequest(accountId, subToCancel, cancellationDate)
  }

  def getCancellationDateFromInvoices(accountSummary: AccountSummary, dateToday: LocalDate): ApiGatewayOp[LocalDate] = {
    val unpaidAndOverdueInvoices = accountSummary.invoices.filter { invoice => invoiceOverdue(invoice, dateToday) }
    if (unpaidAndOverdueInvoices.isEmpty) {
      logger.error(s"Failed to find an unpaid invoice that was overdue. The invoices we got were: ${accountSummary.invoices}")
      ReturnWithResponse(noActionRequired("No unpaid and overdue invoices found!"))
    } else {
      logger.info(s"Found at least one unpaid invoices for account: ${accountSummary.basicInfo.id}. Invoice id(s): ${unpaidAndOverdueInvoices.map(_.id)}")
      implicit val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toEpochDay)
      val earliestDueDate = unpaidAndOverdueInvoices.map(_.dueDate).min
      logger.info(s"Earliest overdue invoice for account ${accountSummary.basicInfo.id} has due date: $earliestDueDate. Setting this as the cancellation date.")
      ContinueProcessing(earliestDueDate)
    }
  }

  def invoiceOverdue(invoice: Invoice, dateToday: LocalDate): Boolean = {
    if (invoice.balance > 0 && invoice.status == "Posted") {
      val zuoraGracePeriod = 14L // This needs to match with the timeframe for the 3rd payment retry attempt in Zuora
      val invoiceOverdueDate = invoice.dueDate.plusDays(zuoraGracePeriod)
      logger.info(s"Zuora grace period is: $zuoraGracePeriod days. Due date for Invoice id ${invoice.id} is ${invoice.dueDate}, so it will be considered overdue on: $invoiceOverdueDate.")
      dateToday.isEqual(invoiceOverdueDate) || dateToday.isAfter(invoiceOverdueDate)
    } else false
  }

  def getSubscriptionToCancel(accountSummary: AccountSummary): ApiGatewayOp[SubscriptionId] = {
    val activeSubs = accountSummary.subscriptions.filter(_.status == "Active")
    activeSubs match {
      case sub :: Nil =>
        logger.info(s"Determined that we should cancel SubscriptionId: ${sub.id} (for AccountId: ${accountSummary.basicInfo.id})")
        ContinueProcessing(sub.id)
      case Nil =>
        logger.error(s"Didn't find any active subscriptions. The full list of subs for this account was: ${accountSummary.subscriptions}")
        ReturnWithResponse(noActionRequired("No Active subscriptions to cancel!"))
      case subs =>
        // This should be a pretty rare scenario, because the Billing Account to Sub relationship is (supposed to be) 1-to-1
        logger.error(s"More than one subscription is Active on account: ${accountSummary.basicInfo.id}. Subscription ids are: ${activeSubs.map(_.id)}")
        ReturnWithResponse(noActionRequired("More than one active sub found!")) // Don't continue because we don't know which active sub to cancel
    }
  }

}
