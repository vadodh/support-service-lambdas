package com.gu.effects.sqs

import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.{ReceiveMessageRequest, SendMessageRequest}
import com.gu.effects.sqs.AwsSQSSend.{Payload, QueueName}
import com.gu.test.EffectsTest
import org.apache.log4j.Logger
import org.scalatest.{AsyncFlatSpec, FlatSpec, Matchers}

import scala.concurrent.Future
import scala.util.{Failure, Random, Success, Try}
import scala.collection.JavaConverters._

class AWSSQSSendTest extends AsyncFlatSpec with Matchers {

  it should "be able to send a message OK" taggedAs EffectsTest in {

    val data = s"hello${Random.nextInt(10000)}"

    val testQueueName = QueueName("test-support-service-effects-tests")
    for {
      aaa <- AwsSQSSend.apply(testQueueName)(Payload(data))
    } yield {
      val bbb = SQSRead(testQueueName)
      bbb should be(List(data))
    }


  }

}

object SQSRead {

  val logger = Logger.getLogger(getClass.getName)

  def apply(queueName: AwsSQSSend.QueueName): List[String] = {
    val sqsClient = AmazonSQSAsyncClientBuilder
      .standard()
      .withCredentials(aws.CredentialsProvider)
      .withRegion(Regions.EU_WEST_1)
      .build()
    val queueUrl = sqsClient.getQueueUrl(queueName.value).getQueueUrl

    logger.info(s"reading message to SQS queue $queueUrl")


    val request = new ReceiveMessageRequest(queueName.value)
      .withAttributeNames("ApproximateReceiveCount")
      .withMaxNumberOfMessages(10)
      .withWaitTimeSeconds(0)

    Try(sqsClient.receiveMessage(request)) match {
      case Success(results) => results.getMessages.asScala.toList.map(_.getBody)
      case Failure(e) =>
        logger.warn("Error encountered while receiving messages from Amazon: " + e.getMessage)
        Nil
    }
  }

}