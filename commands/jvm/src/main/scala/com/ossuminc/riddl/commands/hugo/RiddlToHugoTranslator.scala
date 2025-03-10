/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo

import com.ossuminc.riddl.language.AST.*

trait RiddlToHugoTranslator {

  def writeAdaptor(adaptor: Adaptor): Unit
  def writeContext(context: Context): Unit
  def writeDomain(domain: Domain): Unit
  def writeEntity(entity: Entity): Unit
  def writeEpic(epic: Epic): Unit
  def writeFunction(function: Function): Unit
  def writeProjector(projector: Projector): Unit
  def writeRepository(repo: Repository): Unit
  def writeSaga(saga: Saga): Unit
  def writeStreamlet(stream: Streamlet): Unit

}
