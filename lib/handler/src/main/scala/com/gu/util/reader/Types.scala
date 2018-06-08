package com.gu.util.reader

import com.gu.util.Logging
import com.gu.util.apigateway.ApiGatewayResponse
import com.gu.util.apigateway.ApiGatewayResponse.{badRequest, internalServerError}
import com.gu.util.apigateway.ResponseModels.ApiResponse
import play.api.libs.json.{JsError, JsResult, JsSuccess}
import scalaz.Monad

import scala.util.{Failure, Success, Try}

object Types extends Logging {

  object ApiGatewayOp { // TODO make \/[String, \/[ApiResponse, A]] so we can have errors independently of API gateway

    def ContinueProcessing[A]: A => ApiGatewayOp[A] = (apply[A] _).compose(scalaz.\/-.apply[A])
    def ReturnWithResponse: ApiResponse => ApiGatewayOp[Nothing] = (apply[Nothing] _).compose(scalaz.-\/.apply[ApiResponse])

  }
  case class ApiGatewayOp[+A](underlying: scalaz.\/[ApiResponse, A]) {

    def flatMap[B](f: A => ApiGatewayOp[B]): ApiGatewayOp[B] = ApiGatewayOp {
      underlying.flatMap(f.andThen(_.underlying))
    }

    def map[B](f: A => B): ApiGatewayOp[B] = ApiGatewayOp {
      underlying.map(f)
    }

    def mapResponse(f: ApiResponse => ApiResponse): ApiGatewayOp[A] = ApiGatewayOp {
      underlying.leftMap(f)
    }

  }

  implicit val apiGatewayOpM: Monad[ApiGatewayOp] = {
    val a = implicitly[Monad[({ type Q[X] = scalaz.\/[ApiResponse, X] })#Q]]
    new Monad[ApiGatewayOp] {
      override def bind[A, B](fa: ApiGatewayOp[A])(f: A => ApiGatewayOp[B]): ApiGatewayOp[B] = ApiGatewayOp(a.bind(fa.underlying)(f.andThen(_.underlying)))

      override def point[A](a: => A): ApiGatewayOp[A] = ApiGatewayOp(scalaz.\/-(a))
    }
  }

  implicit class ApiResponseOps(apiGatewayOp: ApiGatewayOp[ApiResponse]) {

    def apiResponse: ApiResponse =
      apiGatewayOp.underlying.fold(identity, identity)

  }

  import Types.ApiGatewayOp._

  // handy classes for converting things
  implicit class JsResultOps[A](jsResult: JsResult[A]) {

    def toApiGatewayOp(response: ApiResponse = badRequest): ApiGatewayOp[A] = {
      jsResult match {
        case JsSuccess(value, _) => ContinueProcessing(value)
        case JsError(error) => {
          logger.error(s"Error when deserializing JSON from API Gateway: $error")
          ReturnWithResponse(response)
        }
      }
    }

    def toApiGatewayOp(error5xx: String): ApiGatewayOp[A] = {
      jsResult match {
        case JsSuccess(apiGatewayCallout, _) => ContinueProcessing(apiGatewayCallout)
        case JsError(error) => {
          logger.error(s"Error when parsing JSON: $error")
          ReturnWithResponse(ApiGatewayResponse.internalServerError(error5xx))
        }
      }
    }

  }

  implicit class OptionOps[A](theOption: Option[A]) {

    def toApiGatewayOp(NoneResponse: ApiResponse): ApiGatewayOp[A] = {
      theOption match {
        case Some(value) => ContinueProcessing(value)
        case None => {
          ReturnWithResponse(NoneResponse)
        }
      }
    }

  }

  // handy classes for converting things
  implicit class TryOps[A](theTry: Try[A]) {

    def toApiGatewayOp(action: String): ApiGatewayOp[A] = {
      theTry match {
        case Success(success) => ContinueProcessing(success)
        case Failure(error) => {
          logger.error(s"Failed to $action: $error")
          ReturnWithResponse(internalServerError(s"Failed to execute lambda - unable to $action"))
        }
      }
    }

  }

  implicit class UnderlyingOps[A](theEither: scalaz.\/[ApiResponse, A]) {

    def toApiGatewayOp: ApiGatewayOp[A] =
      theEither match {
        case scalaz.\/-(success) => ContinueProcessing(success)
        case scalaz.-\/(finished) => ReturnWithResponse(finished)
      }

  }

  // handy class for converting things
  implicit class EitherOps[L, A](theEither: scalaz.\/[L, A]) {

    def toApiGatewayOp(action: String): ApiGatewayOp[A] =
      theEither match {
        case scalaz.\/-(success) => ContinueProcessing(success)
        case scalaz.-\/(error) =>
          logger.error(s"Failed to $action: $error")
          ReturnWithResponse(internalServerError(s"Failed to execute lambda - unable to $action"))
      }

  }

  implicit class LogImplicit[A](op: A) {

    // this is just a handy method to add logging to the end of any for comprehension
    def withLogging(message: String): A = {
      logger.info(s"$message: continued processing with value: $op")
      op
    }

  }

}
