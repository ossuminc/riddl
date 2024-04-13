/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** The interface that plugins must implement */

trait PluginInterface {
  def interfaceVersion: Int = Plugin.interfaceVersion
  def riddlVersion: String = com.ossuminc.riddl.utils.RiddlBuildInfo.version
  def pluginName: String
  def pluginVersion: String
}
