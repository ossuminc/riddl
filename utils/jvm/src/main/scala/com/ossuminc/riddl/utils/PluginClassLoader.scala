/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils
import java.net.{URL, URLClassLoader}

/** Loads a plugin using a [[java.net.URLClassLoader]].
  */
case class PluginClassLoader(urls: List[URL], parentClassLoader: ClassLoader)
    extends URLClassLoader(urls.toArray, parentClassLoader) {
  require(urls.forall(_.getProtocol == "jar"))
  override protected def loadClass(
    name: String,
    resolve: Boolean
  ): Class[?] = { // has the class loaded already?
    var loadedClass: Class[?] = findLoadedClass(name)
    if loadedClass == null then { loadedClass = super.loadClass(name, resolve) }
    if resolve then { // marked to resolve
      resolveClass(loadedClass)
    }
    loadedClass
  }
}
