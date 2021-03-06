package com.gu.newproduct.api.addsubscription.email.paper

import java.time.format.DateTimeFormatter

import com.gu.newproduct.api.addsubscription.Formatters._
import com.gu.newproduct.api.addsubscription.email.PaperEmailData
import com.gu.newproduct.api.addsubscription.zuora.GetContacts.Contacts
import com.gu.newproduct.api.addsubscription.zuora.GetPaymentMethod.{DirectDebit, NonDirectDebitMethod, PaymentMethod}
import com.gu.newproduct.api.addsubscription.zuora.PaymentMethodType
import com.gu.newproduct.api.addsubscription.zuora.PaymentMethodType._
import com.gu.newproduct.api.productcatalog.PlanId._
import play.api.libs.json.{Json, Writes}

object PaperEmailDataSerialiser {
  implicit val writes: Writes[PaperEmailData] = (data: PaperEmailData) => {
    val fields: Map[String, String] = PaperEmailFields(data)
    Json.toJson(fields)
  }
}

object PaperEmailFields {

  val digipackPlans = List(VoucherWeekendPlus, VoucherEveryDayPlus, VoucherSixDayPlus, VoucherSundayPlus, VoucherSaturdayPlus)
  val dateformat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  def apply(
    data: PaperEmailData
  ) = {
    Map(
      "ZuoraSubscriberId" -> data.subscriptionName.value,
      "SubscriberKey" -> data.contacts.billTo.email.map(_.value).getOrElse(""),
      "subscriber_id" -> data.subscriptionName.value,
      "IncludesDigipack" -> digipackPlans.contains(data.plan.id).toString,
      "date_of_first_paper" -> data.firstPaperDate.format(dateformat),
      "date_of_first_payment" -> data.firstPaymentDate.format(dateformat),
      "package" -> data.plan.description.value,
      "subscription_rate" -> data.plan.paymentPlans.get(data.currency).map(_.description).getOrElse("")
    ) ++ paymentMethodFields(data.paymentMethod) ++ addressFields(data.contacts)

  }

  def toDescription(methodType: PaymentMethodType) = methodType match {
    case CreditCardReferenceTransaction | CreditCard => "Credit/Debit Card"
    case BankTransfer => "Direct Debit"
    case PayPal => "PayPal"
    case Other => "" //should not happen
  }

  def paymentMethodFields(paymentMethod: PaymentMethod) = paymentMethod match {
    case DirectDebit(status, accountName, accountNumberMask, sortCode, mandateId) => Map(
      "bank_account_no" -> accountNumberMask.value,
      "bank_sort_code" -> sortCode.hyphenated,
      "account_holder" -> accountName.value,
      "mandate_id" -> mandateId.value,
      "payment_method" -> toDescription(BankTransfer)
    )
    case NonDirectDebitMethod(_, paymentMethodType) => Map(
      "payment_method" -> toDescription(paymentMethodType)
    )
  }

  def addressFields(contacts: Contacts) = {
    val soldTo = contacts.soldTo
    val billTo = contacts.billTo
    val soldToAddress = soldTo.address
    val billToAddress = billTo.address
    Map(
      "title" -> soldTo.title.map(_.value).getOrElse(""),
      "first_name" -> soldTo.firstName.value,
      "last_name" -> soldTo.lastName.value,
      "EmailAddress" -> billTo.email.map(_.value).getOrElse(""),

      "billing_address_line_1" -> billToAddress.address1.map(_.value).getOrElse(""),
      "billing_address_line_2" -> billToAddress.address2.map(_.value).getOrElse(""),
      "billing_address_town" -> billToAddress.city.map(_.value).getOrElse(""),
      "billing_county" -> billToAddress.state.map(_.value).getOrElse(""),
      "billing_postcode" -> billToAddress.postcode.map(_.value).getOrElse(""),
      "billing_country" -> billToAddress.country.map(_.name).getOrElse(""),

      "delivery_address_line_1" -> soldToAddress.address1.map(_.value).getOrElse(""),
      "delivery_address_line_2" -> soldToAddress.address2.map(_.value).getOrElse(""),
      "delivery_address_town" -> soldToAddress.city.map(_.value).getOrElse(""),
      "delivery_county" -> soldToAddress.state.map(_.value).getOrElse(""),
      "delivery_postcode" -> soldToAddress.postcode.map(_.value).getOrElse(""),
      "delivery_country" -> soldToAddress.country.name
    )
  }
}

