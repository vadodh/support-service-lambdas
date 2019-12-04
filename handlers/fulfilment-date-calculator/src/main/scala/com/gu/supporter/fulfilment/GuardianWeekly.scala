package com.gu.supporter.fulfilment

import java.time.{DayOfWeek, LocalDate}
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters

/**
 * @param issueDayOfWeek                Weekday corresponding to publication issue date printed on the paper, for example, Friday for GW
 * @param fulfilmentGenerationDayOfWeek Weekday corresponding to when fulfilment-lambdas generate files
 */
sealed abstract class FulfilmentConstants(
  val issueDayOfWeek: DayOfWeek,
  val fulfilmentGenerationDayOfWeek: DayOfWeek
) {
  def holidayStopFirstAvailableDate(today: LocalDate): LocalDate
}

object GuardianWeeklyFulfilmentDates extends FulfilmentConstants(
  issueDayOfWeek = DayOfWeek.FRIDAY,
  fulfilmentGenerationDayOfWeek = DayOfWeek.THURSDAY,
) {
  def apply(today: LocalDate): FulfilmentDates =
    FulfilmentDates(
      today,
      acquisitionsStartDate(today),
      deliveryAddressChangeEffectiveDate(today),
      holidayStopFirstAvailableDate(today),
      finalFulfilmentFileGenerationDate(today),
      nextAffectablePublicationDateOnFrontCover(today)
    )

  val minDaysBetweenTodayAndFirstAvailableDate = 4

  def holidayStopFirstAvailableDate(today: LocalDate): LocalDate = {
    val dayAfterNextPublicationDay = TemporalAdjusters.next(issueDayOfWeek.plus(1)) // Saturday because GW is published on Friday, https://stackoverflow.com/a/29010338/5205022
    if (DAYS.between(today, today `with` dayAfterNextPublicationDay) < minDaysBetweenTodayAndFirstAvailableDate)
      (today `with` dayAfterNextPublicationDay `with` dayAfterNextPublicationDay) // Saturday after next
    else
      (today `with` dayAfterNextPublicationDay) // next Saturday
  }

  // Cover date of first issue sent to the new address.
  def deliveryAddressChangeEffectiveDate(today: LocalDate): LocalDate =
    nextAffectablePublicationDateOnFrontCover(today)

  // TODO: Take into account bank holidays
  def nextAffectablePublicationDateOnFrontCover(today: LocalDate): LocalDate = {
    val nextFriday = TemporalAdjusters.next(issueDayOfWeek)

    val todayIsFufilmentDay = today.getDayOfWeek equals fulfilmentGenerationDayOfWeek

    if(todayIsFufilmentDay)
      (today `with` nextFriday `with` nextFriday `with` nextFriday)
    else
      (today `with` nextFriday `with` nextFriday)
  }

  def acquisitionsStartDate(today: LocalDate): LocalDate = {
    val nextFriday = TemporalAdjusters.next(issueDayOfWeek)
    today `with` nextFriday `with` nextFriday
  }

  // When was the fulfilment file generated for the nextAffectablePublicationDateOnFrontCover
  def finalFulfilmentFileGenerationDate(today: LocalDate): LocalDate = {
    val previousThursday = TemporalAdjusters.previous(fulfilmentGenerationDayOfWeek)
    nextAffectablePublicationDateOnFrontCover(today) `with` previousThursday `with` previousThursday
  }

}
