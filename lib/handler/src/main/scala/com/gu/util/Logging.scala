package com.gu.util

import com.gu.util.reader.Types.{ WithDepsFailableOp, _ }
import org.apache.log4j.Logger

import scalaz.{ -\/, \/, \/- }

trait Logging {

  val logger = Logger.getLogger(getClass.getName)

  implicit class LogImplicit[A, D](configHttpFailableOp: WithDepsFailableOp[D, A]) {

    // this is just a handy method to add logging to the end of any for comprehension
    def withLogging(message: String): WithDepsFailableOp[D, A] = {

      (configHttpFailableOp.run map {
        _.withLogging(message)
      }).toEitherT

    }

  }

  implicit class LogImplicit2[E, A](failableOp: E \/ A) {

    // this is just a handy method to add logging to the end of any for comprehension
    def withLogging(message: String): E \/ A = {

      failableOp match {
        case \/-(continuation) =>
          logger.info(s"$message: continued processing with value: $continuation")
          \/-(continuation)
        case -\/(response) =>
          logger.error(s"$message: returned here with value: $response")
          -\/(response) // todo some day make an error object with a backtrace...
      }

    }

  }

}
