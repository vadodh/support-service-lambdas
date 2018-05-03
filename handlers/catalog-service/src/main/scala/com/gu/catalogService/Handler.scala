package com.gu.catalogService

import com.amazonaws.services.s3.model.{PutObjectRequest, PutObjectResult}
import com.gu.effects.RawEffects
import com.gu.util.apigateway.LoadConfig
import com.gu.util.reader.Types._
import com.gu.util.zuora.{ZuoraRestConfig, ZuoraRestRequestMaker}
import com.gu.util.{Logging, Stage}
import okhttp3.{Request, Response}
import play.api.libs.json.{Json, Reads}

import scala.util.Try

object Handler extends Logging {

  case class StepsConfig(zuoraRestConfig: ZuoraRestConfig)
  implicit val stepsConfigReads: Reads[StepsConfig] = Json.reads[StepsConfig]

  // this is the entry point
  // it's referenced by the cloudformation so make sure you keep it in step
  def apply(): Unit = {
    runWithEffects(RawEffects.response, RawEffects.stage, RawEffects.s3Load, RawEffects.s3Write)
  }

  // this does the wiring except side effects but not any decisions, so can be used for an end to end test
  def runWithEffects(
    response: Request => Response,
    stage: Stage,
    s3Load: Stage => Try[String],
    s3Write: PutObjectRequest => Try[PutObjectResult]
  ): Unit = {

    val attempt = for {
      config <- LoadConfig.default[StepsConfig](implicitly)(stage, s3Load(stage)).withLogging("loaded config").leftMap(_.body)
      zuoraRequests = ZuoraRestRequestMaker(response, config.stepsConfig.zuoraRestConfig)
      fetchCatalogAttempt <- ZuoraReadCatalog(zuoraRequests).leftMap(_.message)
      uploadCatalogAttempt <- S3UploadCatalog(stage, fetchCatalogAttempt, s3Write)
    } yield ()

    attempt.fold(failureReason => throw CatalogServiceException(failureReason), identity)

  }

  case class CatalogServiceException(message: String) extends Throwable(message: String)

}