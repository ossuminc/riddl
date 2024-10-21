package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{DOMPlatformIOContext, PlatformIOContext}
package object parsing {
  given io: PlatformIOContext = DOMPlatformIOContext()
}
