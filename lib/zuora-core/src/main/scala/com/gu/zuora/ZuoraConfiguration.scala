package com.gu.zuora

import zio.Task

trait ZuoraConfiguration {
  def zuoraConfiguration: ZuoraConfiguration.Service[Any]
}

object ZuoraConfiguration {
  trait Service[R] {
    val zuoraConfig: Task[ZuoraConfig]
  }
}
