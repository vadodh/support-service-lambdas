package com.gu.newproduct.api.productcatalog

import java.time.DayOfWeek
import java.time.DayOfWeek.{MONDAY, SATURDAY, SUNDAY}

import com.gu.newproduct.api.productcatalog.PlanId._
import com.gu.util.config.Stage

import scala.util.Try

object NewProductApiCatalog {
  def catalog1(
    pricesFromZuora: Stage => Try[List[PlanWithPrice]],
    stage: Stage
  ): Try[Catalog] = {

    val voucherWindowRule = WindowRule(
      maybeCutOffDay = Some(DayOfWeek.TUESDAY),
      maybeStartDelay = Some(DelayDays(20)),
      maybeSize = Some(WindowSizeDays(28))
    )

    def voucherDateRules(allowedDays: List[DayOfWeek]) = StartDateRules(Some(DaysOfWeekRule(allowedDays)), Some(voucherWindowRule))

    val voucherMondayRules = voucherDateRules(List(MONDAY))
    val voucherSundayDateRules = voucherDateRules(List(SUNDAY))
    val voucherSaturdayDateRules = voucherDateRules(List(SATURDAY))

    val monthlyContributionWindow = WindowRule(
      maybeSize = Some(WindowSizeDays(1)),
      maybeCutOffDay = None,
      maybeStartDelay = None
    )
    val monthlyContributionRules = StartDateRules(windowRule = Some(monthlyContributionWindow))

    pricesFromZuora(stage).map { prices =>
      val planIdToPrice: Map[PlanId, Option[AmountMinorUnits]] = prices.map(p => p.planId -> p.maybepriceMinorUnits).toMap

      def getPaymentPlanFor(planId: PlanId): Option[PaymentPlan] = {
        val maybePrice: Option[AmountMinorUnits] = planIdToPrice.get(planId).flatten
        maybePrice.map { price =>
          val priceInPounds = BigDecimal(price.value * 100).setScale(2) // todo move the formatter we use for the emails into an implicit thingy
          PaymentPlan(s"GBP $priceInPounds every month")
        }
      }

      Catalog(
        voucherWeekendPlus = Plan(VoucherWeekendPlus, voucherSaturdayDateRules, getPaymentPlanFor(VoucherWeekendPlus)),
        voucherWeekend = Plan(VoucherWeekend, voucherSaturdayDateRules, getPaymentPlanFor(VoucherWeekend)),
        voucherSixDay = Plan(VoucherSixDay, voucherMondayRules, getPaymentPlanFor(VoucherSixDay)),
        voucherSixDayPlus = Plan(VoucherSixDayPlus, voucherMondayRules, getPaymentPlanFor(VoucherSixDayPlus)),
        voucherEveryDay = Plan(VoucherEveryDay, voucherMondayRules, getPaymentPlanFor(VoucherEveryDay)),
        voucherEveryDayPlus = Plan(VoucherEveryDayPlus, voucherMondayRules, getPaymentPlanFor(VoucherEveryDayPlus)),
        voucherSaturday = Plan(VoucherSaturday, voucherSaturdayDateRules, getPaymentPlanFor(VoucherSaturday)),
        voucherSaturdayPlus = Plan(VoucherSaturdayPlus, voucherSaturdayDateRules, getPaymentPlanFor(VoucherSaturdayPlus)),
        voucherSunday = Plan(VoucherSunday, voucherSundayDateRules, getPaymentPlanFor(VoucherSunday)),
        voucherSundayPlus = Plan(VoucherSundayPlus, voucherSundayDateRules, getPaymentPlanFor(VoucherSundayPlus)),
        monthlyContribution = Plan(MonthlyContribution, monthlyContributionRules)
      )
    }
  }

  val catalog = {
    def monthlyPayment(priceInPounds: String) = Some(PaymentPlan(s"£$priceInPounds every month"))

    val voucherWindowRule = WindowRule(
      maybeCutOffDay = Some(DayOfWeek.TUESDAY),
      maybeStartDelay = Some(DelayDays(20)),
      maybeSize = Some(WindowSizeDays(28))
    )

    def voucherDateRules(allowedDays: List[DayOfWeek]) = StartDateRules(Some(DaysOfWeekRule(allowedDays)), Some(voucherWindowRule))

    val voucherMondayRules = voucherDateRules(List(MONDAY))
    val voucherSundayDateRules = voucherDateRules(List(SUNDAY))
    val voucherSaturdayDateRules = voucherDateRules(List(SATURDAY))

    val monthlyContributionWindow = WindowRule(
      maybeSize = Some(WindowSizeDays(1)),
      maybeCutOffDay = None,
      maybeStartDelay = None
    )
    val monthlyContributionRules = StartDateRules(windowRule = Some(monthlyContributionWindow))

    Catalog(
      voucherWeekendPlus = Plan(VoucherWeekendPlus, voucherSaturdayDateRules, monthlyPayment("29.42")),
      voucherWeekend = Plan(VoucherWeekend, voucherSaturdayDateRules, monthlyPayment("20.76")),
      voucherSixDay = Plan(VoucherSixDay, voucherMondayRules, monthlyPayment("41.12")),
      voucherSixDayPlus = Plan(VoucherSixDayPlus, voucherMondayRules, monthlyPayment("47.62")),
      voucherEveryDay = Plan(VoucherEveryDay, voucherMondayRules, monthlyPayment("47.62")),
      voucherEveryDayPlus = Plan(VoucherEveryDayPlus, voucherMondayRules, monthlyPayment("51.96")),
      voucherSaturday = Plan(VoucherSaturday, voucherSaturdayDateRules, monthlyPayment("10.36")),
      voucherSaturdayPlus = Plan(VoucherSaturdayPlus, voucherSaturdayDateRules, monthlyPayment("21.62")),
      voucherSunday = Plan(VoucherSunday, voucherSundayDateRules, monthlyPayment("10.79")),
      voucherSundayPlus = Plan(VoucherSundayPlus, voucherSundayDateRules, monthlyPayment("22.06")),
      monthlyContribution = Plan(MonthlyContribution, monthlyContributionRules)
    )
  }
}
