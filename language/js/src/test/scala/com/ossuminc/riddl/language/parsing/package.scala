package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{DOMPlatformContext, PlatformContext}
package object parsing {
  given io: PlatformContext = DOMPlatformContext()
}
