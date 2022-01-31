package com.yoppworks.ossum.riddl.idea

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey


object RIDDLIdeaPlugin {
  private final val BUNDLE = "messages.RiddlIdeaPlugin"
  private final val INSTANCE = new RIDDLIdeaPlugin

  @Nls def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String = {
    INSTANCE.getMessage(key, params)
  }
}

class RIDDLIdeaPlugin private() extends DynamicBundle(RIDDLIdeaPlugin.BUNDLE) {
}
