package com.gu.util.config

import com.gu.util.config.ConfigReads.ConfigFailure
import com.gu.util.config.LoadConfigModule.S3Location
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.Json
import scalaz.{-\/, \/-}

import scala.util.Try

class LoadConfigModuleTest extends FlatSpec with Matchers {

  def fakeS3Load(response: String)(location: S3Location): Try[String] = Try {
    if (location.bucket != "gu-reader-revenue-private") throw (new RuntimeException(s"test failed: unexpected bucket name ${location.bucket}"))
    if (location.key == "membership/support-service-lambdas/PROD/someDir/filename-PROD.v2.json") response
    else
      throw (new RuntimeException(s"test failed unexpected key ${location.key}"))
  }

  val prodStage = Stage("PROD")

  case class TestConfig(someValue: String, someOtherValue: Int)

  object TestConfig {
    implicit val reads = Json.reads[TestConfig]
    implicit val location = ConfigLocation[TestConfig](path = "someDir/filename", version = 2)
  }

  it should "be able to load config successfully with version" in {

    def prodS3Load = fakeS3Load(prodJson) _

    val prodConfig = LoadConfigModule(prodStage, prodS3Load)

    prodConfig[TestConfig] shouldBe \/-(TestConfig("prodValue", 92))

  }

  it should "fail if the configuration file is missing fields" in {

    val jsonMissingSomeValue =
      """
        |{
        |"stage" : "PROD",
        | "someOtherValue" : 22
        |}
      """.stripMargin
    def invalidJsonLoad = fakeS3Load(jsonMissingSomeValue) _

    val prodConfig = LoadConfigModule(prodStage, invalidJsonLoad)
    prodConfig[TestConfig].isLeft shouldBe (true)

  }

  it should "fail if the configuration file contains fields of the wrong type" in {

    val jsonMissingSomeValue =
      """
        |{
        |"stage" : "PROD",
        | "someValue" : "something",
        | "someOtherValue" : "this should be an Int"
        |}
      """.stripMargin
    def invalidJsonLoad = fakeS3Load(jsonMissingSomeValue) _

    val prodConfig = LoadConfigModule(prodStage, invalidJsonLoad)
    prodConfig[TestConfig].isLeft shouldBe (true)

  }

  it should "fail if the configuration is invalid json" in {

    def invalidJsonLoad = fakeS3Load("hello world") _

    val prodConfig = LoadConfigModule(prodStage, invalidJsonLoad)

    prodConfig[TestConfig].isLeft shouldBe (true)
  }

  it should "fail if the stage in the config file differs from the expected stage provided" in {

    //note this will return the dev json when asking for the prod stage
    def wrongFileS3Load = fakeS3Load(devJSon) _

    val prodConfig = LoadConfigModule(prodStage, wrongFileS3Load)

    prodConfig[TestConfig] shouldBe -\/(ConfigFailure("Expected to load PROD config, but loaded DEV config"))
  }

  it should "fail if no stage variable in configuration file" in {

    val noStageConfig =
      """{
        |  "someValue": "prodValue",
        |  "someOtherValue" : 92
        |  }
      """.stripMargin
    def noStageS3Load = fakeS3Load(noStageConfig) _

    val prodConfig = LoadConfigModule(prodStage, noStageS3Load)

    prodConfig[TestConfig].isLeft shouldBe (true)
  }

  it should "fail if the stage variable in configuration file is not a string" in {

    val noStageConfig =
      """{
        |   "stage" : 134
        |  "someValue": "prodValue",
        |  "someOtherValue" : 92
        |  }
      """.stripMargin
    def invalidStageS3Load = fakeS3Load(noStageConfig) _

    val prodConfig = LoadConfigModule(prodStage, invalidStageS3Load)

    prodConfig[TestConfig].isLeft shouldBe (true)
  }

  val prodJson: String =
    """
      |{ "stage": "PROD",
      |  "someValue": "prodValue",
      |  "someOtherValue" : 92
      |}
    """.stripMargin

  val devJSon: String =
    """
      |{ "stage": "DEV",
      |  "someValue": "devValue",
      |  "someOtherValue" : 93
      |}
    """.stripMargin

}
