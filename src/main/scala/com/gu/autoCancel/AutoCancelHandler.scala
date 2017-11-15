package com.gu.autoCancel

import com.gu.effects.RawEffects
import com.gu.util.Logging
import com.gu.util.apigateway.ApiGatewayHandler

object AutoCancelHandler extends App with Logging {

  // this is the entry point
  // it's referenced by the cloudformation so make sure you keep it in step
  def handleRequest = ApiGatewayHandler(RawEffects.default) {
    AutoCancelSteps()
  }_

}
