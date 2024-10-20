package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{PlatformIOContext,DOMPlatformIOContext}
package object parsing {
  given io: PlatformIOContext = DOMPlatformIOContext()
}
