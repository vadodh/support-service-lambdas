import java.time.LocalDate

import com.github.tototoshi.csv.CSVReader
import com.gu.holiday_stops.subscription.RatePlanChargeBillingSchedule

class Main extends App {
  val fileName = args(0)

  val reader = CSVReader.open(fileName)

  val records: Iterator[Map[String, String]] = reader.iteratorWithHeaders

  while(records.hasNext) {
    val recordsForSubs: List[Map[String, String]] = readRecordsForSub(records)
    checkInvoiceDates(recordsForSubs)
  }

  def readRecordsForSub(records: Iterator[Map[String, String]]) = {
    val firstRecord = records.next()
    firstRecord ::
      records.takeWhile { record =>
        (record("subscription_name") == firstRecord("subscription_name")) &&
        (record("rate_plan_charge_name") == firstRecord("rate_plan_charge_name"))
      }.toList
  }

  def checkInvoiceDates(recordsForSub: List[Map[String, String]]) = {
    val allInvoiceDates = recordsForSub.map(record => LocalDate.parse(record("invoice_date")))

    recordsForSub.foreach { record =>
      val id = s"${record("subscription_name")}-${record("invoice_number")}"

      RatePlanChargeBillingSchedule(
        LocalDate.parse(record("customer_acceptance_date")),
        record.get("billing_day"),
        record.get("trigger_event"),
        record.get("trigger_date").map(LocalDate.parse),
        None,
        record("bill_cycle_day").toInt,
        record.get("up_to_periods_type"),
        record.get("up_to_periods").map(_.toInt),
        record.get("billing_period"),
        record.get("specific_billing_period").map(_.toInt),
        record.get("end_date_condition") //TODO: add to datalake table
      ).fold(
        error => println(s"Failed to generate schedule for $id: $error"),
        { schedule =>
          val invoiceDate = LocalDate.parse(record("invoice_date"))
          schedule.billDatesCoveringDate(invoiceDate).fold(
            { error => println(s"Could not get billing period for $id for date $invoiceDate: $error") },
            { billDates =>
              if (!allInvoiceDates.contains(billDates.startDate)) {
                s"$id had invoices on dates $allInvoiceDates which did not include the bill dates calculated for date $invoiceDate: $billDates"
              }
            }
          )
        }
      )
    }
  }
}
