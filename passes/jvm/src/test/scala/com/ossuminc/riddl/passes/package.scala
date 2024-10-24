package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{JVMPlatformIOContext, PlatformIOContext}

import scala.concurrent.ExecutionContext

package object passes {
  given pc: PlatformIOContext = JVMPlatformIOContext()
  implicit val ec: ExecutionContext = pc.ec
}
