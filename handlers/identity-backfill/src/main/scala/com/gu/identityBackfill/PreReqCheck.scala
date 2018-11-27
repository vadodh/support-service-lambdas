package com.gu.identityBackfill

import com.gu.identityBackfill.TypeConvert._
import com.gu.identityBackfill.Types._
import com.gu.identityBackfill.salesforce.UpdateSalesforceIdentityId.IdentityId
import com.gu.identityBackfill.zuora.GetZuoraSubTypeForAccount
import com.gu.identityBackfill.zuora.GetZuoraSubTypeForAccount.ReaderType.ReaderTypeValue
import com.gu.salesforce.TypesForSFEffectsData.SFContactId
import com.gu.util.apigateway.ApiGatewayResponse
import com.gu.util.reader.Types.ApiGatewayOp._
import com.gu.util.reader.Types._
import com.gu.util.resthttp.Types.ClientFailableOp

object PreReqCheck {

  case class PreReqResult(zuoraAccountIds: Set[Types.AccountId], sFContactIds: Set[SFContactId], existingIdentityId: Option[IdentityId])

  def apply(
    findExistingIdentityId: EmailAddress => ApiGatewayOp[Option[IdentityId]],
    findAndValidateZuoraAccounts: EmailAddress => ApiGatewayOp[List[ZuoraAccountIdentitySFContact]],
    noZuoraAccountsForIdentityId: IdentityId => ApiGatewayOp[Unit],
    zuoraSubType: AccountId => ApiGatewayOp[Unit],
    syncableSFToIdentity: List[SFContactId] => ApiGatewayOp[Unit]
  )(emailAddress: EmailAddress): ApiGatewayOp[PreReqResult] = {
    for {
      maybeExistingIdentityId <- findExistingIdentityId(emailAddress)
      zuoraAccounts <- findAndValidateZuoraAccounts(emailAddress)
      _ <- maybeExistingIdentityId.map(noZuoraAccountsForIdentityId).getOrElse(ContinueProcessing(()))
      _ <- syncableSFToIdentity(zuoraAccounts.map(_.sfContactId))
    } yield PreReqResult(zuoraAccounts.map(_.accountId).toSet, zuoraAccounts.map(_.sfContactId).toSet, maybeExistingIdentityId)
  }

  def checkSfContactsSyncable(syncableSFToIdentity: SFContactId => ApiGatewayOp[Unit])(sFContactIds: List[SFContactId]): ApiGatewayOp[Unit] = {
    val failures = sFContactIds
      .map(syncableSFToIdentity)
      .zip(sFContactIds)
      .collect {
        case (returnWithResponse: ReturnWithResponse, sfContactId) => (sfContactId -> returnWithResponse.resp.body).toString
      }

    if (failures.isEmpty)
      ContinueProcessing(())
    else
      ReturnWithResponse(ApiGatewayResponse.badRequest(s"multiple contacts are not syncable ${failures.mkString(", ")}"))
  }

  def noZuoraAccountsForIdentityId(
    countZuoraAccountsForIdentityId: ClientFailableOp[Int]
  ): ApiGatewayOp[Unit] = {
    for {
      zuoraAccountsForIdentityId <- countZuoraAccountsForIdentityId.toApiGatewayOp("count zuora accounts for identity id")
      _ <- (zuoraAccountsForIdentityId == 0).toApiGatewayContinueProcessing(ApiGatewayResponse.notFound("already used that identity id"))
    } yield ()
  }

  def validateZuoraAccountsFound(zuoraAccountsRetrieved: ClientFailableOp[List[ZuoraAccountIdentitySFContact]])(emailAddress: EmailAddress): ApiGatewayOp[List[ZuoraAccountIdentitySFContact]] = {

    def validateOneCrmId(zuoraAccountsForEmail: List[ZuoraAccountIdentitySFContact]) = {
      val uniqueCrmIds = zuoraAccountsForEmail.map(_.crmId).distinct.size

      if (uniqueCrmIds == 1)
        ContinueProcessing(())
      else if (uniqueCrmIds == 0)
        ReturnWithResponse(ApiGatewayResponse.badRequest(s"no zuora accounts found for $emailAddress"))
      else
        ReturnWithResponse(ApiGatewayResponse.badRequest(s"multiple CRM ids found for $emailAddress $zuoraAccountsForEmail"))
    }

    def validateNoIdentityIdsForEmail(zuoraAccountsForEmail: List[ZuoraAccountIdentitySFContact]) = {
      if (zuoraAccountsForEmail.forall(_.identityId.isEmpty))
        ContinueProcessing(())
      else
        ReturnWithResponse(ApiGatewayResponse.badRequest(s"identity ids found in zuora $emailAddress $zuoraAccountsForEmail"))
    }

    for {
      zuoraAccountsForEmail <- zuoraAccountsRetrieved.toApiGatewayOp(s"get zuora accounts for $emailAddress")
      _ <- validateOneCrmId(zuoraAccountsForEmail)
      _ <- validateNoIdentityIdsForEmail(zuoraAccountsForEmail)
    } yield zuoraAccountsForEmail
  }

  def acceptableReaderType(readerTypes: ClientFailableOp[List[GetZuoraSubTypeForAccount.ReaderType]]): ApiGatewayOp[Unit] = {
    for {
      readerTypes <- readerTypes.toApiGatewayOp("checking acceptable reader type in zuora")
      incorrectReaderTypes = readerTypes.collect {
        case ReaderTypeValue(readerType) if readerType != "Direct" => readerType // it's bad
      }
      _ <- incorrectReaderTypes.isEmpty
        .toApiGatewayContinueProcessing(ApiGatewayResponse.notFound(s"had an incorrect reader type(s): ${incorrectReaderTypes.mkString(",")}"))
    } yield ()
  }

}
