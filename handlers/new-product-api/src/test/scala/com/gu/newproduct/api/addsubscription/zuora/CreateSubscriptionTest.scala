package com.gu.newproduct.api.addsubscription.zuora

import java.time.LocalDate

import com.gu.newproduct.api.addsubscription.Handler.{PlanAndCharge, ProductRatePlanChargeId, ProductRatePlanId}
import com.gu.newproduct.api.addsubscription.ZuoraAccountId
import com.gu.newproduct.api.addsubscription.zuora.CreateSubscription.WireModel.{ChargeOverrides, SubscribeToRatePlans, WireCreateRequest}
import com.gu.newproduct.api.addsubscription.zuora.CreateSubscription.{CaseId, CreateReq}
import com.gu.util.zuora.RestRequestMaker.{GenericError, RequestsPost, WithCheck}
import org.scalatest.{FlatSpec, Matchers}
import scalaz.{-\/, \/-}

class CreateSubscriptionTest extends FlatSpec with Matchers {

  it should "get account as object" in {

    val ids = PlanAndCharge(
      productRatePlanId = ProductRatePlanId("hiProductRatePlanId"),
      productRatePlanChargeId = ProductRatePlanChargeId("hiProductRatePlanChargeId")
    )
    val expectedReq = WireCreateRequest(
      accountKey = "zac",
      autoRenew = true,
      contractEffectiveDate = "2018-07-17",
      termType = "TERMED",
      renewalTerm = 12,
      initialTerm = 12,
      AcquisitionCase__c = "casecase",
      subscribeToRatePlans = List(
        SubscribeToRatePlans(
          productRatePlanId = "hiProductRatePlanId",
          chargeOverrides = List(ChargeOverrides(price = 1.25, productRatePlanChargeId = "hiProductRatePlanChargeId"))
        )
      )
    )
    val accF: RequestsPost[WireCreateRequest, Unit] = {
      case (req, "subscriptions", WithCheck) if req == expectedReq =>
        \/-(())
      case in => -\/(GenericError(s"bad request: $in"))
    }
    val createReq = CreateReq(
      accountId = ZuoraAccountId("zac"),
      amountMinorUnits = 125,
      start = LocalDate.of(2018, 7, 17),
      acquisitionCase = CaseId("casecase")
    )
    val actual = CreateSubscription(ids, accF)(createReq)
    actual shouldBe \/-(())
  }
}

