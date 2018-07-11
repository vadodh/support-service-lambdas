package com.gu.sf_contact_merge

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import com.gu.effects.{GetFromS3, RawEffects}
import com.gu.sf_contact_merge.GetZuoraEmailsForAccounts.{AccountId, EmailAddress}
import com.gu.util.apigateway.ApiGatewayHandler.{LambdaIO, Operation}
import com.gu.util.apigateway.{ApiGatewayHandler, ApiGatewayRequest, ApiGatewayResponse, ResponseModels}
import com.gu.util.config.LoadConfigModule.StringFromS3
import com.gu.util.config.{LoadConfigModule, Stage, TrustedApiConfig}
import com.gu.util.reader.Types.ApiGatewayOp.{ContinueProcessing, ReturnWithResponse}
import com.gu.util.reader.Types._
import com.gu.util.zuora.RestRequestMaker.ClientFailableOp
import com.gu.util.zuora.{ZuoraQuery, ZuoraRestConfig, ZuoraRestRequestMaker}
import okhttp3.{Request, Response}
import play.api.libs.json.{Json, Reads}

object Handler {

  case class StepsConfig(zuoraRestConfig: ZuoraRestConfig)

  implicit val stepsConfigReads: Reads[StepsConfig] = Json.reads[StepsConfig]

  // Referenced in Cloudformation
  def apply(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {
    runWithEffects(RawEffects.stage, GetFromS3.fetchString, RawEffects.response, LambdaIO(inputStream, outputStream, context))
  }

  def runWithEffects(stage: Stage, fetchString: StringFromS3, getResponse: Request => Response, lambdaIO: LambdaIO) = {

    val loadConfig = LoadConfigModule(stage, fetchString)
    ApiGatewayHandler(lambdaIO) {
      for {
        trustedApiConfig <- loadConfig[TrustedApiConfig].toApiGatewayOp("load trusted Api config")
        zuoraRestConfig <- loadConfig[ZuoraRestConfig].toApiGatewayOp("load trusted Api config")
        requests = ZuoraRestRequestMaker(getResponse, zuoraRestConfig)
        zuoraQuerier = ZuoraQuery(requests)
        wiredSteps = steps(GetZuoraEmailsForAccounts(zuoraQuerier))_
        configuredOp = Operation.noHealthcheck(wiredSteps, false)
      } yield (trustedApiConfig, configuredOp)
    }

  }

  case class WireSfContactRequest(
    fullContactId: String,
    billingAccountZuoraIds: List[String],
    accountId: String
  )
  implicit val readWireSfContactRequest = Json.reads[WireSfContactRequest]

  def steps(getZuoraEmails: List[AccountId] => ClientFailableOp[List[Option[EmailAddress]]])(req: ApiGatewayRequest): ResponseModels.ApiResponse =
    (for {
      input <- req.bodyAsCaseClass[WireSfContactRequest]()
      accountIds = input.billingAccountZuoraIds.map(AccountId.apply)
      emailAddresses <- getZuoraEmails(accountIds).toApiGatewayOp("get zuora emails")
      _ <- AssertSameEmails(emailAddresses)
      // todo add the update call in the next PR
    } yield ApiGatewayResponse.notFound("passed the prereq check")).apiResponse

}

object AssertSameEmails {

  def apply(emailAddresses: List[Option[EmailAddress]]): ApiGatewayOp[Unit] = {
    if (emailAddresses.distinct.size == 1) ContinueProcessing(()) else ReturnWithResponse(ApiGatewayResponse.notFound("those zuora accounts had differing emails"))
  }

}

object UpdateZuoraSFAccountAndContact {
  //sfContactId__c
  //CrmId
}