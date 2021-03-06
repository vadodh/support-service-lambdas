package com.gu.newproduct.api.addsubscription.zuora

import java.time.LocalDate

import com.gu.newproduct.api.addsubscription.zuora.CreateSubscription.WireModel.{ChargeOverrides, SubscribeToRatePlans, WireCreateRequest, WireSubscription}
import com.gu.newproduct.api.addsubscription.zuora.CreateSubscription.{ChargeOverride, SubscriptionName, ZuoraCreateSubRequest}
import com.gu.newproduct.api.addsubscription._
import com.gu.newproduct.api.productcatalog.AmountMinorUnits
import com.gu.newproduct.api.productcatalog.ZuoraIds.{PlanAndCharge, ProductRatePlanChargeId, ProductRatePlanId}
import com.gu.util.resthttp.RestRequestMaker.{RequestsPost, WithCheck}
import com.gu.util.resthttp.Types.{ClientSuccess, GenericError}
import org.scalatest.{FlatSpec, Matchers}

class CreateSubscriptionTest extends FlatSpec with Matchers {

  def currentDate = () => LocalDate.of(2018, 7, 2)
  it should "get account as object" in {

    val ids = PlanAndCharge(
      productRatePlanId = ProductRatePlanId("hiProductRatePlanId"),
      productRatePlanChargeId = ProductRatePlanChargeId("hiProductRatePlanChargeId")
    )
    val expectedReq = WireCreateRequest(
      accountKey = "zac",
      autoRenew = true,
      contractEffectiveDate = "2018-07-02",
      customerAcceptanceDate = "2018-07-27",
      termType = "TERMED",
      renewalTerm = 12,
      initialTerm = 12,
      AcquisitionCase__c = "casecase",
      AcquisitionSource__c = "sourcesource",
      CreatedByCSR__c = "csrcsr",
      subscribeToRatePlans = List(
        SubscribeToRatePlans(
          productRatePlanId = "hiProductRatePlanId",
          chargeOverrides = List(ChargeOverrides(price = 1.25, productRatePlanChargeId = "hiProductRatePlanChargeId"))
        )
      )
    )
    val accF: RequestsPost[WireCreateRequest, WireSubscription] = {
      case (req, "subscriptions", WithCheck) if req == expectedReq =>
        ClientSuccess(WireSubscription("a-s123"))
      case in => GenericError(s"bad request: $in")
    }
    val createReq = ZuoraCreateSubRequest(
      productRatePlanId = ids.productRatePlanId,
      accountId = ZuoraAccountId("zac"),
      maybeChargeOverride = Some(ChargeOverride(
        AmountMinorUnits(125),
        ids.productRatePlanChargeId
      )),

      acceptanceDate = LocalDate.of(2018, 7, 27),
      acquisitionCase = CaseId("casecase"),
      acquisitionSource = AcquisitionSource("sourcesource"),
      createdByCSR = CreatedByCSR("csrcsr")
    )
    val actual = CreateSubscription(accF, currentDate)(createReq)
    actual shouldBe ClientSuccess(SubscriptionName("a-s123"))
  }
}

