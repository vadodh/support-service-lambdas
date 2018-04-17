package com.gu.digitalSubscriptionExpiry.zuora

import java.io
import java.time.{LocalDate, LocalTime, ZoneId, ZonedDateTime}

import com.gu.digitalSubscriptionExpiry.Handler.StepsConfig
import com.gu.digitalSubscriptionExpiry.zuora.GetAccountSummary.AccountId
import com.gu.digitalSubscriptionExpiry.zuora.GetSubscription.{RatePlan, RatePlanCharge, SubscriptionId, SubscriptionName, SubscriptionResult}
import com.gu.effects.{ConfigLoad, RawEffects}
import com.gu.test.EffectsTest
import com.gu.util.zuora.ZuoraDeps
import com.gu.util.{Config, Stage}
import org.scalatest.{FlatSpec, Matchers}
import scalaz.{-\/, \/, \/-}
import scalaz.syntax.std.either._
import com.gu.digitalSubscriptionExpiry.common.CommonApiResponses._
class GetSubscriptionEffectsTest extends FlatSpec with Matchers {

  it should "return not found if sub id is invalid" taggedAs EffectsTest in {
    val testSubscriptionId = SubscriptionId("invalidSubId")

    val actual: \/[io.Serializable, SubscriptionResult] = for {
      configAttempt <- ConfigLoad.load(Stage("DEV")).toEither.disjunction
      config <- Config.parseConfig[StepsConfig](configAttempt)
      deps: ZuoraDeps = ZuoraDeps(RawEffects.createDefault.response, config.stepsConfig.zuoraRestConfig)
      subscription <- GetSubscription(deps)(testSubscriptionId)
    } yield {
      subscription
    }
    actual should be(-\/(notFoundResponse))
  }

  it should "successfully get subscription info against dev" taggedAs EffectsTest in {
    val testSubscriptionId = SubscriptionId("A-S00044160")

    val actual: \/[io.Serializable, SubscriptionResult] = for {
      configAttempt <- ConfigLoad.load(Stage("DEV")).toEither.disjunction
      config <- Config.parseConfig[StepsConfig](configAttempt)
      deps: ZuoraDeps = ZuoraDeps(RawEffects.createDefault.response, config.stepsConfig.zuoraRestConfig)
      subscription <- GetSubscription(deps)(testSubscriptionId)
    } yield {
      subscription
    }

    def removeActivationTime(sub: SubscriptionResult) = sub.copy(
      casActivationDate = sub.casActivationDate.map {
        activationDateTime =>
          val activationLocalDate = activationDateTime.toLocalDate
          ZonedDateTime.of(activationLocalDate, LocalTime.of(0, 0, 0), ZoneId.of("GMT"))
      }
    )

    val customerAcceptanceDate = LocalDate.of(2017, 12, 15)
    val expectedActivationDate = ZonedDateTime.of(2018, 4, 13, 0, 0, 0, 0, ZoneId.of("GMT"))
    val startDate = LocalDate.of(2017, 11, 29)
    val expected = SubscriptionResult(
      testSubscriptionId,
      SubscriptionName("2c92c0f860017cd501600893134617b3"),
      AccountId("2c92c0f860017cd501600893130317a7"),
      Some(expectedActivationDate),
      customerAcceptanceDate,
      startDate,
      startDate.plusYears(1),
      List(
        RatePlan(
          "Promotions",
          List(RatePlanCharge(
            name = "Discount template",
            effectiveStartDate = LocalDate.of(2017, 12, 15),
            effectiveEndDate = LocalDate.of(2018, 3, 15)
          ))
        ),
        RatePlan(
          "Digital Pack",
          List(RatePlanCharge(
            name = "Digital Pack Monthly",
            effectiveStartDate = LocalDate.of(2017, 12, 15),
            effectiveEndDate = LocalDate.of(2018, 11, 29)
          ))
        )
      )
    )

    actual.map(removeActivationTime) should be(\/-(expected))
  }
}
