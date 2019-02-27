package com.gu.newproduct.api.productcatalog

import java.time.DayOfWeek
import java.time.DayOfWeek._

import com.gu.i18n.Currency
import com.gu.newproduct.api.addsubscription.Formatters._
import com.gu.newproduct.api.productcatalog.PlanId._

sealed trait BillingPeriod
object Monthly extends BillingPeriod
object Annual extends BillingPeriod

object NewProductApi {
  def catalog(pricingFor: PlanId => Map[Currency, AmountMinorUnits]): Catalog = {

    def paymentPlansFor(planid:PlanId, billingPeriod: BillingPeriod): Map[Currency, PaymentPlan] = {
      val pricesByCurrency: Map[Currency, AmountMinorUnits] = pricingFor(planid)

      val billingPeriodDescription = billingPeriod match {
        case Monthly => "every month"
        case Annual => "every 12 months"
      }
      pricesByCurrency.map{ case (currency, amount) => (currency, PaymentPlan(s"${currency.iso} ${amount.formatted} $billingPeriodDescription"))}
    }



    val voucherWindowRule = WindowRule(
      maybeCutOffDay = Some(DayOfWeek.TUESDAY),
      maybeStartDelay = Some(DelayDays(20)),
      maybeSize = Some(WindowSizeDays(35))
    )

    def voucherDateRules(allowedDays: List[DayOfWeek]) = StartDateRules(Some(DaysOfWeekRule(allowedDays)), Some(voucherWindowRule))

    val voucherMondayRules = voucherDateRules(List(MONDAY))
    val voucherSundayDateRules = voucherDateRules(List(SUNDAY))
    val voucherSaturdayDateRules = voucherDateRules(List(SATURDAY))

    val homeDeliveryWindowRule = WindowRule(
      maybeCutOffDay = None,
      maybeStartDelay = Some(DelayDays(3)),
      maybeSize = Some(WindowSizeDays(28))
    )

    def homeDeliveryDateRules(allowedDays: Option[List[DayOfWeek]]) = StartDateRules(allowedDays.map(DaysOfWeekRule), Some(homeDeliveryWindowRule))

    val homeDeliveryEveryDayRules = homeDeliveryDateRules(None)
    val weekDays = List(
      MONDAY,
      TUESDAY,
      WEDNESDAY,
      THURSDAY,
      FRIDAY
    )

    val homeDeliverySixDayRules = homeDeliveryDateRules(Some(weekDays ++ List(SATURDAY)))
    val homeDeliverySundayDateRules = homeDeliveryDateRules(Some(List(SUNDAY)))
    val homeDeliverySaturdayDateRules = homeDeliveryDateRules(Some(List(SATURDAY)))
    val homeDeliveryWeekendRules = homeDeliveryDateRules(Some(List(SATURDAY, SUNDAY)))

    val todayOnlyRule = StartDateRules(
      windowRule = Some(WindowRule(
        maybeSize = Some(WindowSizeDays(1)),
        maybeCutOffDay = None,
        maybeStartDelay = None
      ))
    )

    val windowStartingIn2Weeks =  WindowRule(
      maybeCutOffDay = None,
      maybeStartDelay = Some(DelayDays(14)),
      maybeSize =None
    )

    val startsin2WeeksAtLeast = StartDateRules(
      windowRule = Some(windowStartingIn2Weeks)
    )

    def planWithPayment(
      planId: PlanId,
      planDescription: PlanDescription,
      startDateRules: StartDateRules,
      billingPeriod: BillingPeriod
    ) = Plan(planId, planDescription, startDateRules, paymentPlansFor(planId, billingPeriod))

    Catalog(
      voucherWeekendPlus = planWithPayment(VoucherWeekendPlus, PlanDescription("Weekend+"), voucherSaturdayDateRules, Monthly),
      voucherWeekend = planWithPayment(VoucherWeekend, PlanDescription("Weekend"), voucherSaturdayDateRules, Monthly),
      voucherSixDay = planWithPayment(VoucherSixDay, PlanDescription("Sixday"), voucherMondayRules, Monthly),
      voucherSixDayPlus = planWithPayment(VoucherSixDayPlus, PlanDescription("Sixday+"), voucherMondayRules, Monthly),
      voucherEveryDay = planWithPayment(VoucherEveryDay, PlanDescription("Everyday"), voucherMondayRules, Monthly),
      voucherEveryDayPlus = planWithPayment(VoucherEveryDayPlus, PlanDescription("Everyday+"), voucherMondayRules, Monthly),
      voucherSaturday = planWithPayment(VoucherSaturday, PlanDescription("Saturday"), voucherSaturdayDateRules, Monthly),
      voucherSaturdayPlus = planWithPayment(VoucherSaturdayPlus, PlanDescription("Saturday+"), voucherSaturdayDateRules, Monthly),
      voucherSunday = planWithPayment(VoucherSunday, PlanDescription("Sunday"), voucherSundayDateRules, Monthly),
      voucherSundayPlus = planWithPayment(VoucherSundayPlus, PlanDescription("Sunday+"), voucherSundayDateRules, Monthly),
      monthlyContribution = planWithPayment(MonthlyContribution, PlanDescription("Monthly"), todayOnlyRule, Monthly),
      annualContribution = planWithPayment(AnnualContribution, PlanDescription("Annual"), todayOnlyRule, Monthly),
      homeDeliveryEveryDay = planWithPayment(HomeDeliveryEveryDay, PlanDescription("Everyday"), homeDeliveryEveryDayRules, Monthly),
      homeDeliverySaturday = planWithPayment(HomeDeliverySaturday, PlanDescription("Saturday"), homeDeliverySaturdayDateRules, Monthly),
      homeDeliverySunday = planWithPayment(HomeDeliverySunday, PlanDescription("Sunday"), homeDeliverySundayDateRules, Monthly),
      homeDeliverySixDay = planWithPayment(HomeDeliverySixDay, PlanDescription("Sixday"), homeDeliverySixDayRules, Monthly),
      homeDeliveryWeekend = planWithPayment(HomeDeliveryWeekend, PlanDescription("Weekend"), homeDeliveryWeekendRules, Monthly),
      homeDeliveryEveryDayPlus = planWithPayment(HomeDeliveryEveryDayPlus, PlanDescription("Everyday+"), homeDeliveryEveryDayRules, Monthly),
      homeDeliverySaturdayPlus = planWithPayment(HomeDeliverySaturdayPlus, PlanDescription("Saturday+"), homeDeliverySaturdayDateRules, Monthly),
      homeDeliverySundayPlus = planWithPayment(HomeDeliverySundayPlus, PlanDescription("Sunday+"), homeDeliverySundayDateRules, Monthly),
      homeDeliverySixDayPlus = planWithPayment(HomeDeliverySixDayPlus, PlanDescription("Sixday+"), homeDeliverySixDayRules, Monthly),
      homeDeliveryWeekendPlus = planWithPayment(HomeDeliveryWeekendPlus, PlanDescription("Weekend+"), homeDeliveryWeekendRules, Monthly),
      digipackAnnual = planWithPayment(DigipackAnnual, PlanDescription("Annual"), startsin2WeeksAtLeast, Annual),
      digipackMonthly = planWithPayment(DigipackMonthly, PlanDescription("Monthly"), startsin2WeeksAtLeast, Monthly),
    )
  }

}
