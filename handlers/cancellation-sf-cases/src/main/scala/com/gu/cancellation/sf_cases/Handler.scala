package com.gu.cancellation.sf_cases

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import com.gu.effects.RawEffects
import com.gu.salesforce.auth.SalesforceAuthenticate
import com.gu.salesforce.auth.SalesforceAuthenticate.SFAuthConfig
import com.gu.salesforce.cases.SalesforceCase
import com.gu.salesforce.cases.SalesforceCase.Raise.{NewCase, RaiseCaseResponse}
import com.gu.salesforce.cases.SalesforceCase.Update.CaseUpdate
import com.gu.util.Logging
import com.gu.util.apigateway.ApiGatewayHandler.{LambdaIO, Operation}
import com.gu.util.apigateway.ResponseModels.ApiResponse
import com.gu.util.apigateway.{ApiGatewayHandler, ApiGatewayRequest, ApiGatewayResponse}
import com.gu.util.config.ConfigReads.ConfigFailure
import com.gu.util.config.{Config, LoadConfig, Stage}
import com.gu.util.reader.Types._
import com.gu.util.zuora.RestRequestMaker
import okhttp3.{Request, Response}
import play.api.libs.json.{Json, Reads}
import scalaz.\/

object Handler extends Logging {

  case class StepsConfig(sfConfig: SFAuthConfig)
  implicit val stepsConfigReads: Reads[StepsConfig] = Json.reads[StepsConfig]

  def raiseCase(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit =
    runWithEffects(RaiseCase.steps, RawEffects.response, RawEffects.stage, RawEffects.s3Load, LambdaIO(inputStream, outputStream, context))

  object RaiseCase {

    case class RaiseRequestBody(product: String)
    implicit val reads = Json.reads[RaiseRequestBody]

    implicit val writes = Json.writes[RaiseCaseResponse]

    def embellishRaiseRequestBody(raiseRequestBody: RaiseRequestBody) = NewCase(
      Origin = "Self Service",
      Product__c = raiseRequestBody.product,
      Journey__c = "SV - At Risk - MB",
      Enquiry_Type__c = "Canx Reason Chosen",
      Status = "Closed",
      Subject = "Online Cancellation Attempt"
    )

    def steps(sfRequests: ApiGatewayOp[RestRequestMaker.Requests])(apiGatewayRequest: ApiGatewayRequest) =
      (for {
        sfRequests <- sfRequests
        raiseCaseDetail <- apiGatewayRequest.bodyAsCaseClass[RaiseRequestBody]()
        raiseCaseResponse <- SalesforceCase.Raise(sfRequests)(embellishRaiseRequestBody(raiseCaseDetail)).toApiGatewayOp("raise case")
      } yield ApiResponse("200", Json.prettyPrint(Json.toJson(raiseCaseResponse)))).apiResponse

  }

  def updateCase(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit =
    runWithEffects(UpdateCase.steps, RawEffects.response, RawEffects.stage, RawEffects.s3Load, LambdaIO(inputStream, outputStream, context))

  object UpdateCase {

    case class UpdateRequestBody(Description: String)
    implicit val reads = Json.reads[UpdateRequestBody]

    case class UpdateCasePathParams(caseId: String)
    implicit val pathParamReads = Json.reads[UpdateCasePathParams]

    def steps(sfRequests: ApiGatewayOp[RestRequestMaker.Requests])(apiGatewayRequest: ApiGatewayRequest) =
      (for {
        sfRequests <- sfRequests
        pathParams <- apiGatewayRequest.pathParamsAsCaseClass[UpdateCasePathParams]()
        updateCaseDetail <- apiGatewayRequest.bodyAsCaseClass[UpdateRequestBody]()
        _ <- SalesforceCase.Update(sfRequests)(pathParams.caseId, CaseUpdate()).toApiGatewayOp("update case")
      } yield ApiGatewayResponse.successfulExecution).apiResponse

  }

  def runWithEffects(steps: ApiGatewayOp[RestRequestMaker.Requests] => ApiGatewayRequest => ApiResponse, response: Request => Response, stage: Stage, s3Load: Stage => ConfigFailure \/ String, lambdaIO: LambdaIO) = {

    def operation: Config[StepsConfig] => Operation = config => {

      val sfRequests = SalesforceAuthenticate(response, config.stepsConfig.sfConfig)

      Operation.noHealthcheck(steps(sfRequests), false)

    }

    ApiGatewayHandler[StepsConfig](lambdaIO)(for {
      config <- LoadConfig.default[StepsConfig](implicitly)(stage, s3Load(stage)).toApiGatewayOp("load config")
      configuredOp = operation(config)
    } yield (config, configuredOp))

  }

}
