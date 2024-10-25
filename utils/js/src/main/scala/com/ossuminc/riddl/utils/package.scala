package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{DOMPlatformIOContext, PlatformIOContext}

import scala.concurrent.ExecutionContext

package object utils {
  given pc: PlatformIOContext = DOMPlatformIOContext()
  implicit val ec: ExecutionContext = pc.ec
}
