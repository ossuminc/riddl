/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils
import java.net.URL
import java.net.URLClassLoader

/** Loads a plugin using a [[java.net.URLClassLoader]].
  */
case class PluginClassLoader(
  urls: List[URL],
  parentClassLoader: ClassLoader)
    extends URLClassLoader(urls.toArray, parentClassLoader) {
  require(urls.forall(_.getProtocol == "jar"))
  override protected def loadClass(
    name: String,
    resolve: Boolean
  ): Class[_] = { // has the class loaded already?
    var loadedClass: Class[_] = findLoadedClass(name)
    if (loadedClass == null) { loadedClass = super.loadClass(name, resolve) }
    if (resolve) { // marked to resolve
      resolveClass(loadedClass)
    }
    loadedClass
  }
}
