package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{DOMPlatformContext, PlatformContext}

package object passes {
  given pc: PlatformContext = DOMPlatformContext()
}
