package com.gu.newproduct.api.addsubscription.zuora

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.gu.newproduct.api.addsubscription._
import com.gu.newproduct.api.productcatalog.AmountMinorUnits
import com.gu.newproduct.api.productcatalog.ZuoraIds.{ProductRatePlanChargeId, ProductRatePlanId}
import com.gu.util.resthttp.ClientFailableOpLogging.LogImplicit2
import com.gu.util.resthttp.RestRequestMaker.{RequestsPost, WithCheck}
import com.gu.util.resthttp.Types.ClientFailableOp
import play.api.libs.json.{Json, Reads}

object CreateSubscription {

  object WireModel {

    case class WireSubscription(subscriptionNumber: String)

    implicit val readsResponse: Reads[WireSubscription] = Json.reads[WireSubscription]

    case class ChargeOverrides(
      price: Double,
      productRatePlanChargeId: String
    )

    implicit val writesCharge = Json.writes[ChargeOverrides]

    case class SubscribeToRatePlans(
      productRatePlanId: String,
      chargeOverrides: List[ChargeOverrides]
    )

    implicit val writesSubscribe = Json.writes[SubscribeToRatePlans]

    case class WireCreateRequest(
      accountKey: String,
      autoRenew: Boolean = true,
      contractEffectiveDate: String,
      customerAcceptanceDate: String,
      termType: String = "TERMED",
      renewalTerm: Int = 12,
      initialTerm: Int = 12,
      subscribeToRatePlans: List[SubscribeToRatePlans],
      AcquisitionCase__c: String,
      AcquisitionSource__c: String,
      CreatedByCSR__c: String
    )

    implicit val writesRequest = Json.writes[WireCreateRequest]
  }

  import WireModel._

  def createRequest(currentDate: LocalDate, createSubscription: ZuoraCreateSubRequest): WireCreateRequest = {
    import createSubscription._
    WireCreateRequest(
      accountKey = accountId.value,
      contractEffectiveDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
      customerAcceptanceDate = acceptanceDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
      AcquisitionCase__c = acquisitionCase.value,
      AcquisitionSource__c = acquisitionSource.value,
      CreatedByCSR__c = createdByCSR.value,
      subscribeToRatePlans = List(
        SubscribeToRatePlans(
          productRatePlanId = productRatePlanId.value,
          chargeOverrides = maybeChargeOverride.map(chargeOverride =>
            ChargeOverrides(
              price = chargeOverride.amountMinorUnits.value.toDouble / 100,
              productRatePlanChargeId = chargeOverride.productRatePlanChargeId.value
            )).toList
        )
      )
    )
  }

  case class ChargeOverride(
    amountMinorUnits: AmountMinorUnits,
    productRatePlanChargeId: ProductRatePlanChargeId
  )
  case class ZuoraCreateSubRequest(
    productRatePlanId: ProductRatePlanId,
    accountId: ZuoraAccountId,
    maybeChargeOverride: Option[ChargeOverride],
    acceptanceDate: LocalDate,
    acquisitionCase: CaseId,
    acquisitionSource: AcquisitionSource,
    createdByCSR: CreatedByCSR
  )

  object ZuoraCreateSubRequest {
    def apply(
      request: AddSubscriptionRequest,
      acceptanceDate: LocalDate,
      chargeOverride: Option[ChargeOverride],
      productRatePlanId: ProductRatePlanId
    ): ZuoraCreateSubRequest = ZuoraCreateSubRequest(
      productRatePlanId = productRatePlanId,
      accountId = request.zuoraAccountId,
      maybeChargeOverride = chargeOverride,
      acceptanceDate = acceptanceDate,
      acquisitionCase = request.acquisitionCase,
      acquisitionSource = request.acquisitionSource,
      createdByCSR = request.createdByCSR
    )
  }
  case class SubscriptionName(value: String) extends AnyVal

  def apply(
    post: RequestsPost[WireCreateRequest, WireSubscription],
    currentDate: () => LocalDate
  )(createSubscription: ZuoraCreateSubRequest): ClientFailableOp[SubscriptionName] = {
    val maybeWireSubscription = post(createRequest(currentDate(), createSubscription), s"subscriptions", WithCheck)
    maybeWireSubscription.map { wireSubscription =>
      SubscriptionName(wireSubscription.subscriptionNumber)
    }.withLogging("created subscription")
  }

}
