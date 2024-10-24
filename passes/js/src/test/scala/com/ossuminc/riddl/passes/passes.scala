package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{DOMPlatformIOContext, PlatformIOContext}

package object passes {
  given pc: PlatformIOContext = DOMPlatformIOContext()
}
