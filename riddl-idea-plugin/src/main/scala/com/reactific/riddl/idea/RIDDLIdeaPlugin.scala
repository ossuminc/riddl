package com.reactific.riddl.idea

import com.intellij.DynamicBundle
import org.jetbrains.annotations.{Nls, PropertyKey}


object RIDDLIdeaPlugin {
  private final val BUNDLE = "messages.RiddlIdeaPlugin"
  private final val INSTANCE = new RIDDLIdeaPlugin

  @Nls def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String = {
    INSTANCE.getMessage(key, params)
  }
}

class RIDDLIdeaPlugin private() extends DynamicBundle(RIDDLIdeaPlugin.BUNDLE) {
}
