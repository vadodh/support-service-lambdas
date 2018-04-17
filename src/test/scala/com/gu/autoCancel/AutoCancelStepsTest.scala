package com.gu.autoCancel

import java.time.LocalDate

import com.gu.TestData
import com.gu.autoCancel.AutoCancel.AutoCancelRequest
import com.gu.autoCancel.AutoCancelDataCollectionFilter.ACFilterDeps
import com.gu.effects.TestingRawEffects
import com.gu.effects.TestingRawEffects.BasicRequest
import com.gu.util.reader.Types._
import com.gu.util.zuora.ZuoraAccount.{AccountId, PaymentMethodId}
import com.gu.util.zuora.ZuoraGetAccountSummary.{AccountSummary, BasicAccountInfo, Invoice, SubscriptionSummary}
import com.gu.util.zuora.ZuoraModels._
import org.scalatest._
import scalaz.\/-

class AutoCancelStepsTest extends FlatSpec with Matchers {

  val basicInfo = BasicAccountInfo(AccountId("id123"), 11.99, PaymentMethodId("pmid"))
  val subscription = SubscriptionSummary(SubscriptionId("sub123"), "A-S123", "Active")
  val singleOverdueInvoice = Invoice("inv123", LocalDate.now.minusDays(14), 11.99, "Posted")

  "auto cancel filter 2" should "cancel attempt" in {
    val aCDeps = ACFilterDeps(
      now = LocalDate.now,
      getAccountSummary = _ => \/-(AccountSummary(basicInfo, List(subscription), List(singleOverdueInvoice)))
    )
    val autoCancelCallout = AutoCancelHandlerTest.fakeCallout(true)
    val cancel: FailableOp[AutoCancelRequest] = AutoCancelDataCollectionFilter(aCDeps)(autoCancelCallout)

    cancel should be(\/-(AutoCancelRequest("id123", SubscriptionId("sub123"), LocalDate.now.minusDays(14))))
  }

  "auto cancel" should "turn off auto pay" in {
    val effects = new TestingRawEffects(false, 200)
    AutoCancel(TestData.zuoraDeps(effects))(AutoCancelRequest("AID", SubscriptionId("subid"), LocalDate.now))

    effects.requestsAttempted should contain(BasicRequest("PUT", "/accounts/AID", "{\"autoPay\":false}"))
  }

  //  // todo need an ACSDeps so we don't need so many mock requests
  //  "auto cancel step" should "turn off auto pay and send email" in {
  //    val effects = new TestingRawEffects(false, 200)
  //    val autoCancelJson =
  //      """
  //        |{"accountId": "AID", "autoPay": "true", "paymentMethodType": "GoldBars"}
  //      """.stripMargin // should probaly base on a real payload
  //    val fakeRequest = ApiGatewayRequest(Some(URLParams(None, None, Some("false"))), autoCancelJson)
  //    val acDeps = ACSDeps()
  //    AutoCancelSteps(acDeps)(AutoCancelRequest("AID", SubscriptionId("subid"), LocalDate.now)).run.run(effects.configHttp)
  //
  //    val requests = effects.result.map { request =>
  //      val buffer = new Buffer()
  //      request.body().writeTo(buffer)
  //      val body = buffer.readString(UTF_8)
  //      val url = request.url
  //      (request.method(), url.encodedPath(), body)
  //    }
  //
  //    requests should contain(("PUT", "/accounts/AID", "{\"autoPay\":false}"))
  //    requests should contain(("POST", "/EMAILSEND/AID", "{\"autoPay\":false}"))
  //  }

}
