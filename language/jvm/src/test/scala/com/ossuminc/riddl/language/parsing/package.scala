package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.{PlatformIOContext, JVMPlatformIOContext}

package object parsing {
  given PlatformIOContext = JVMPlatformIOContext()
}
