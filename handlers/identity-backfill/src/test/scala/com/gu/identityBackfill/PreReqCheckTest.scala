package com.gu.identityBackfill

import com.gu.identity.GetByEmail.NotFound
import com.gu.identityBackfill.PreReqCheck.PreReqResult
import com.gu.identityBackfill.Types.{IdentityId, _}
import com.gu.util.apigateway.ApiGatewayResponse
import org.scalatest.{FlatSpec, Matchers}
import scalaz.{-\/, \/-}

class PreReqCheckTest extends FlatSpec with Matchers {

  it should "go through a happy case" in {

    val result =
      PreReqCheck(
        email => \/-(IdentityId("asdf")),
        email => \/-(ZuoraAccountIdentitySFContact(AccountId("acc"), None, SFContactId("sf"))),
        identityId => \/-(()),
        _ => \/-(())
      )(EmailAddress("email@address"))

    val expectedResult = \/-(PreReqResult(AccountId("acc"), SFContactId("sf"), IdentityId("asdf")))
    result should be(expectedResult)
  }

  it should "go through a already got identity (according to the zuora query bu identity id) case without calling update" in {

    val result =
      PreReqCheck.noZuoraAccountsForIdentityId(countZuoraAccountsForIdentityId = _ => \/-(1))(IdentityId("asdf"))

    val expectedResult = -\/(ApiGatewayResponse.notFound("already used that identity id"))
    result should be(expectedResult)
  }

  it should "go through a already got identity (according to the zuora account query by email) case without calling update" in {

    val result =
      PreReqCheck.getSingleZuoraAccountForEmail(email =>
        \/-(List(ZuoraAccountIdentitySFContact(AccountId("acc"), Some(IdentityId("haha")), SFContactId("sf")))))(EmailAddress("email@address"))

    val expectedResult = -\/(ApiGatewayResponse.notFound("the account we found was already populated with an identity id"))
    result should be(expectedResult)
  }

  it should "go through a multiple zuora account without calling update even not in dry run mode" in {

    val result =
      PreReqCheck.getSingleZuoraAccountForEmail(email => {
        val contactWithoutIdentity = ZuoraAccountIdentitySFContact(AccountId("acc"), None, SFContactId("sf"))
        \/-(List(contactWithoutIdentity, contactWithoutIdentity))
      })(EmailAddress("email@address"))

    val expectedResult = -\/(ApiGatewayResponse.notFound("should have exactly one zuora account per email at this stage"))
    result should be(expectedResult)
  }

  it should "return a 404 if there's no identity account at all for that email" in {

    val result =
      PreReqCheck(
        email => -\/(NotFound),
        email => ???,
        identityId => ???,
        _ => ???
      )(EmailAddress("email@address"))

    val expectedResult = -\/(ApiGatewayResponse.notFound("user doesn't have identity"))
    result should be(expectedResult)
  }

}
