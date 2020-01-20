package com.gu.delivery_records_api

import java.time.LocalDate

import cats.data.{EitherT, NonEmptyList}
import cats.effect.Effect
import cats.implicits._
import com.gu.salesforce.{Contact, SalesforceHandlerSupport}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.Http4sDsl
import org.http4s.{EntityEncoder, HttpRoutes, Request, Response}

case class DeliveryRecordApiRoutesError(message: String)

object DeliveryRecordApiRoutes {

  def apply[F[_]: Effect](deliveryRecordsService: DeliveryRecordsService[F]): HttpRoutes[F] = {
    type RouteResult[A] = EitherT[F, F[Response[F]], A]
    object http4sDsl extends Http4sDsl[F]
    import http4sDsl._

    def getContactFromHeaders(request: Request[F]): RouteResult[Contact] = {
      SalesforceHandlerSupport
        .extractContactFromHeaders(
          request.headers.toList.map(header => header.name.value -> header.value)
        )
        .toEitherT[F]
        .leftMap(error => BadRequest(error))
    }

    def getDeliveryRecords(
      subscriptionNumber: String,
      contact: Contact,
      optionalStartDate: Option[LocalDate],
      optionalEndDate: Option[LocalDate]
    ): EitherT[F, F[Response[F]], DeliveryRecordsApiResponse] = {
      deliveryRecordsService
        .getDeliveryRecordsForSubscription(subscriptionNumber, contact, optionalStartDate, optionalEndDate)
        .leftMap {
          case error: DeliveryRecordServiceSubscriptionNotFound =>
            NotFound(error)
          case error: DeliveryRecordServiceGenericError =>
            InternalServerError(error)
        }
    }

    def putDeliveryProblemCreditRequests(
      subscriptionNumber: String,
      contact: Contact,
      dates: NonEmptyList[LocalDate]
    ): RouteResult[Unit] =
      deliveryRecordsService.putDeliveryProblemCreditRequests(subscriptionNumber, contact, dates)
        .leftMap {
          case error: DeliveryRecordServiceSubscriptionNotFound =>
            NotFound(error)
          case error: DeliveryRecordServiceGenericError =>
            InternalServerError(error)
        }

    def parseDateFromQueryString(request: Request[F], queryParameterKey: String): EitherT[F, F[Response[F]], Option[LocalDate]] = {
      request
        .params
        .get(queryParameterKey)
        .traverse[RouteResult, LocalDate] {
          queryStringValue =>
            Either
              .catchNonFatal(LocalDate.parse(queryStringValue))
              .leftMap(ex =>
                BadRequest(DeliveryRecordApiRoutesError(
                  s"$queryParameterKey should be formatted yyyy-MM-dd"
                )))
              .toEitherT
        }
    }

    def parseDatesFromQueryString(request: Request[F], queryParameterKey: String): RouteResult[List[LocalDate]] =
      request
        .multiParams
        .getOrElse(queryParameterKey, Nil)
        .map({ queryStringValue =>
          val date: RouteResult[LocalDate] = Either
            .catchNonFatal(LocalDate.parse(queryStringValue))
            .leftMap { _ =>
              BadRequest(DeliveryRecordApiRoutesError(
                s"$queryParameterKey should be formatted yyyy-MM-dd"
              ))
            }.toEitherT
          date
        }).toList.sequence

    def toResponse[A](result: EitherT[F, F[Response[F]], A])(implicit enc: EntityEncoder[F, A]): F[Response[F]] = {
      result
        .fold(
          identity,
          value => Ok(value)
        )
        .flatten
    }

    HttpRoutes.of[F] {

      case request @ GET -> Root / "delivery-records" / subscriptionNumber =>
        toResponse(
          for {
            contact <- getContactFromHeaders(request)
            startDate <- parseDateFromQueryString(request, "startDate")
            endDate <- parseDateFromQueryString(request, "endDate")
            records <- getDeliveryRecords(
              subscriptionNumber,
              contact,
              startDate,
              endDate
            )
          } yield records
        )

      case request @ PUT -> Root / "delivery-records" / "credit" / subscriptionNumber =>
        val result = for {
          contact <- getContactFromHeaders(request)
//          deliveryDates <- parseDatesFromQueryString(request, "date").map{
//            x => NonEmptyList.fromList(x)
//          }.ensure( BadRequest(DeliveryRecordApiRoutesError(
//            s" should be formatted yyyy-MM-dd"
//          )))(g => g.isDefined)
          deliveryDates <- {
            val x = parseDatesFromQueryString(request, "date")
            x.re
          }
          _ <- putDeliveryProblemCreditRequests(subscriptionNumber, contact, deliveryDates)
        } yield ()
        result.fold(identity, _ => NoContent()).flatten
    }
  }
}
