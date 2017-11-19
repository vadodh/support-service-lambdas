package com.gu.util.zuora

import com.gu.util.apigateway.ApiGatewayHandler.StageAndConfigHttp
import com.gu.util.apigateway.ApiGatewayResponse._
import com.gu.util.apigateway.ResponseModels.ApiResponse
import com.gu.util.reader.Types._
import com.gu.util.zuora.ZuoraModels._
import com.gu.util.zuora.ZuoraReaders._
import com.gu.util.{ Logging, ZuoraRestConfig }
import okhttp3._
import play.api.libs.json._

import scalaz.Scalaz._
import scalaz.{ Reader, \/ }

object ZuoraRestRequestMaker extends Logging {

  def convertResponseToCaseClass[T](response: Response)(implicit r: Reads[T]): ApiResponse \/ T = {
    if (response.isSuccessful) {
      val bodyAsJson = Json.parse(response.body.string)
      bodyAsJson.validate[ZuoraCommonFields] match {
        case JsSuccess(ZuoraCommonFields(true), _) =>
          bodyAsJson.validate[T] match {
            case success: JsSuccess[T] => success.get.right
            case error: JsError => {
              logger.info(s"Failed to convert Zuora response to case case 1 $error. Response body was: \n ${bodyAsJson}")
              internalServerError("Error when converting Zuora response to case class").left
            }
          }
        case JsSuccess(zuoraFailure, _) =>
          logger.error(s"Zuora rejected our call $bodyAsJson")
          internalServerError("Received failure result from Zuora during autoCancellation").left
        case error: JsError => {
          logger.info(s"Failed to convert Zuora response to case case 2 $error. Response body was: \n ${bodyAsJson}")
          internalServerError("Error when converting Zuora response to case class").left
        }
      }

    } else {
      logger.error(s"Request to Zuora was unsuccessful, the response was: \n $response")
      internalServerError("Request to Zuora was unsuccessful").left
    }
  }

  def get[RESP](path: String)(implicit r: Reads[RESP]): WithDepsFailableOp[StageAndConfigHttp, RESP] =
    Reader { stageAndConfigHttp: StageAndConfigHttp =>
      val request = buildRequest(stageAndConfigHttp.config)(path).get().build()
      logger.info(s"Getting $path from Zuora")
      val response = stageAndConfigHttp.response(request)
      convertResponseToCaseClass[RESP](response)
    }.toEitherT

  def put[REQ, RESP](req: REQ, path: String)(implicit tjs: Writes[REQ], r: Reads[RESP]): WithDepsFailableOp[StageAndConfigHttp, RESP] =
    Reader { stageAndConfigHttp: StageAndConfigHttp =>
      val body = RequestBody.create(MediaType.parse("application/json"), Json.toJson(req).toString)
      val request = buildRequest(stageAndConfigHttp.config)(path).put(body).build()
      logger.info(s"Attempting to $path with the following command: $req")
      val response = stageAndConfigHttp.response(request)
      convertResponseToCaseClass[RESP](response)
    }.toEitherT

  def buildRequest(config: ZuoraRestConfig)(route: String): Request.Builder =
    new Request.Builder()
      .addHeader("apiSecretAccessKey", config.password)
      .addHeader("apiAccessKeyId", config.username)
      .url(s"${config.baseUrl}/$route")

}
