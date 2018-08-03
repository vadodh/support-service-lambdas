package com.gu.sf_contact_merge

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.Context
import com.gu.effects.{GetFromS3, RawEffects}
import com.gu.sf_contact_merge.TypeConvert._
import com.gu.sf_contact_merge.WireRequestToDomainObject.WireSfContactRequest
import com.gu.sf_contact_merge.update.UpdateAccountSFLinks.{CRMAccountId, LinksFromZuora}
import com.gu.sf_contact_merge.update.UpdateStepsWiring
import com.gu.sf_contact_merge.validate.GetContacts.{AccountId, IdentityId, SFContactId}
import com.gu.sf_contact_merge.validate.GetIdentityAndZuoraEmailsForAccounts.IdentityAndSFContactAndEmail
import com.gu.sf_contact_merge.validate.{GetIdentityAndZuoraEmailsForAccounts, ValidationSteps}
import com.gu.util.apigateway.ApiGatewayHandler.{LambdaIO, Operation}
import com.gu.util.apigateway.{ApiGatewayHandler, ApiGatewayRequest, ApiGatewayResponse, ResponseModels}
import com.gu.util.config.LoadConfigModule.StringFromS3
import com.gu.util.config.{LoadConfigModule, Stage}
import com.gu.util.reader.Types._
import com.gu.util.resthttp.Types.ClientFailableOp
import com.gu.util.zuora.SafeQueryBuilder.MaybeNonEmptyList
import com.gu.util.zuora.{ZuoraQuery, ZuoraRestConfig, ZuoraRestRequestMaker}
import okhttp3.{Request, Response}
import play.api.libs.json.Json
import scalaz.NonEmptyList

object Handler {

  // Referenced in Cloudformation
  def apply(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit =
    ApiGatewayHandler(LambdaIO(inputStream, outputStream, context)) {
      operationForEffects(RawEffects.stage, GetFromS3.fetchString, RawEffects.response)
    }

  def operationForEffects(stage: Stage, fetchString: StringFromS3, getResponse: Request => Response): ApiGatewayOp[Operation] = {
    val loadConfig = LoadConfigModule(stage, fetchString)
    for {
      zuoraRestConfig <- loadConfig[ZuoraRestConfig].toApiGatewayOp("load trusted Api config")
      requests = ZuoraRestRequestMaker(getResponse, zuoraRestConfig)
      zuoraQuerier = ZuoraQuery(requests)
      wiredSteps = Steps(
        GetIdentityAndZuoraEmailsForAccounts(zuoraQuerier),
        ValidationSteps.apply _,
        UpdateStepsWiring(requests.patch, requests)
      ) _
    } yield Operation.noHealthcheck(wiredSteps)
  }

}

object Steps {

  implicit val readWireSfContactRequest = Json.reads[WireSfContactRequest]

  def apply(
    getIdentityAndZuoraEmailsForAccounts: NonEmptyList[AccountId] => ClientFailableOp[List[IdentityAndSFContactAndEmail]],
    validation: (Option[IdentityId], List[IdentityAndSFContactAndEmail]) => ApiGatewayOp[Unit],
    update: (LinksFromZuora, Option[SFContactId], NonEmptyList[AccountId]) => ClientFailableOp[Unit]
  )(req: ApiGatewayRequest): ResponseModels.ApiResponse =
    (for {
      wireInput <- req.bodyAsCaseClass[WireSfContactRequest]()
      mergeRequest <- WireRequestToDomainObject(wireInput)
        .toApiGatewayContinueProcessing(ApiGatewayResponse.badRequest("no account ids supplied"))
      accountAndEmails <- getIdentityAndZuoraEmailsForAccounts(mergeRequest.zuoraAccountIds)
        .toApiGatewayOp("getIdentityAndZuoraEmailsForAccounts")
      _ <- validation(mergeRequest.sFPointer.identityId, accountAndEmails)
      oldContact = accountAndEmails.find(_.identityId.isDefined).map(_.sfContactId)
      _ <- update(mergeRequest.sFPointer, oldContact, mergeRequest.zuoraAccountIds)
        .toApiGatewayOp("update accounts with winning details")
    } yield ApiGatewayResponse.successfulExecution).apiResponse

}

object WireRequestToDomainObject {

  case class WireSfContactRequest(
    fullContactId: String,
    billingAccountZuoraIds: List[String],
    accountId: String,
    identityId: Option[String]
  )
  case class MergeRequest(
    sFPointer: LinksFromZuora,
    zuoraAccountIds: NonEmptyList[AccountId]
  )

  def apply(request: WireSfContactRequest): Option[MergeRequest] =
    MaybeNonEmptyList(request.billingAccountZuoraIds.map(AccountId.apply)).map { accountIds =>
      MergeRequest(
        LinksFromZuora(
          SFContactId(request.fullContactId),
          CRMAccountId(request.accountId),
          request.identityId.map(IdentityId.apply)
        ),
        accountIds
      )
    }

}
