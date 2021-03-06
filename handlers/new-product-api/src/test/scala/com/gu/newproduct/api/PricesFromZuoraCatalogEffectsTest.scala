package com.gu.newproduct.api

import com.gu.effects.GetFromS3
import com.gu.newproduct.api.productcatalog.PlanId._
import com.gu.newproduct.api.productcatalog.{PricesFromZuoraCatalog, ZuoraIds}
import com.gu.test.EffectsTest
import com.gu.util.config.{Stage, ZuoraEnvironment}
import org.scalatest.{FlatSpec, Matchers}

class PricesFromZuoraCatalogEffectsTest extends FlatSpec with Matchers {

  it should "load catalog" taggedAs EffectsTest in {

    val actual = for {
      zuoraIds <- ZuoraIds.zuoraIdsForStage(Stage("DEV")).toDisjunction
      response <- PricesFromZuoraCatalog(ZuoraEnvironment("DEV"), GetFromS3.fetchString, zuoraIds.rateplanIdToApiId.get).toDisjunction
    } yield response
    //the prices might change but at least we can assert that we got some price for each product
    actual.map(_.keySet) shouldBe Right(
      Set(
        VoucherSaturdayPlus,
        VoucherSundayPlus,
        VoucherWeekendPlus,
        VoucherSixDayPlus,
        VoucherEveryDayPlus,
        VoucherSunday,
        VoucherWeekend,
        VoucherSixDay,
        VoucherEveryDay,
        VoucherSaturday,
        HomeDeliveryWeekend,
        HomeDeliverySunday,
        HomeDeliveryEveryDay,
        HomeDeliverySixDay,
        HomeDeliveryWeekendPlus,
        HomeDeliverySundayPlus,
        HomeDeliveryEveryDayPlus,
        HomeDeliverySixDayPlus,
        MonthlyContribution,
        AnnualContribution,
        HomeDeliverySaturday,
        HomeDeliverySaturdayPlus,
        DigipackAnnual,
        DigipackMonthly
      )
    )
  }
}
