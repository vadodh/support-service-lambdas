package com.gu.newproduct.api.addsubscription

import java.time.LocalDate

import com.gu.newproduct.api.productcatalog.PlanId
import play.api.libs.json.{JsError, JsSuccess, Json, Reads}
import scalaz._
import Scalaz._
import scala.util.{Failure, Success, Try}

case class ZuoraAccountId(value: String) extends AnyVal

case class AddSubscriptionRequest(
  zuoraAccountId: ZuoraAccountId,
  startDate: LocalDate,
  acquisitionSource: AcquisitionSource,
  createdByCSR: CreatedByCSR,
  amountMinorUnits: AmountMinorUnits,
  acquisitionCase: CaseId,
  planId: PlanId
)
case class AmountMinorUnits(value: Int) extends AnyVal
case class CaseId(value: String) extends AnyVal
case class AcquisitionSource(value: String) extends AnyVal
case class CreatedByCSR(value: String) extends AnyVal
import scalaz.std.`try`._
object AddSubscriptionRequest {

  case class AddSubscriptionRequestWire(
    zuoraAccountId: String,
    startDate: String,
    acquisitionSource: String,
    createdByCSR: String,
    amountMinorUnits: Int,
    acquisitionCase: String,
    planId: String
  ) {
    def toAddSubscriptionRequest = {
      val parsedRequestOrError = for {
        parsedDate <- toDisjunction(Try(LocalDate.parse(startDate))).leftMap(_ => "invalid date format")
        parsedPlanId <- PlanId.fromName(planId).toRightDisjunction(invalidPlanError)
      } yield AddSubscriptionRequest(
        zuoraAccountId = ZuoraAccountId(zuoraAccountId),
        startDate = parsedDate,
        acquisitionSource = AcquisitionSource(this.acquisitionSource),
        createdByCSR = CreatedByCSR(this.createdByCSR),
        amountMinorUnits = AmountMinorUnits(amountMinorUnits),
        CaseId(acquisitionCase),
        parsedPlanId
      )

      parsedRequestOrError match {
        case \/-(req) => JsSuccess(req)
        case -\/(error) => JsError(error)
      }

    }
    def invalidPlanError = {
      val validValues = PlanId.all.map(_.name).mkString(",")
      s"unsupported plan: allowed values are $validValues"
    }
  }

  val wireReads = Json.reads[AddSubscriptionRequestWire]
  implicit val reads: Reads[AddSubscriptionRequest] = json => wireReads.reads(json).flatMap(_.toAddSubscriptionRequest)
}
