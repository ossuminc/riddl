package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{PlatformIOContext, JVMPlatformIOContext}

package object language {
  given pc: PlatformIOContext = JVMPlatformIOContext()
}
