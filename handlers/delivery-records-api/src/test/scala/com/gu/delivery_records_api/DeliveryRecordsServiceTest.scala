package com.gu.delivery_records_api

import java.time.LocalDate

import cats.data.NonEmptyList
import org.scalatest.{FlatSpec, Matchers}

class DeliveryRecordsServiceTest extends FlatSpec with Matchers {

  "deliveryDateListFilter" should "work with a single date" in {
    val dates = NonEmptyList.one(LocalDate.of(2020, 1, 20))
    DeliveryRecordsService.deliveryDateListFilter(dates) should equal(" WHERE Delivery_Date__c = 2020-01-20")
  }

  it should "work with a list of multiple dates" in {
    val dates = NonEmptyList.of(
      LocalDate.of(2020, 1, 20),
      LocalDate.of(2020, 2, 21),
      LocalDate.of(2020, 3, 22)
    )
    DeliveryRecordsService.deliveryDateListFilter(dates) should equal(
      " WHERE Delivery_Date__c IN (2020-01-20, 2020-02-21, 2020-03-22)"
    )
  }
}
