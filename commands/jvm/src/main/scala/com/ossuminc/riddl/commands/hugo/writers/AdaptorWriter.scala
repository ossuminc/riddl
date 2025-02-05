/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers

import com.ossuminc.riddl.language.AST.{Adaptor, AdaptorContents, Parents}

trait AdaptorWriter { this: MarkdownWriter =>

  def emitAdaptor(adaptor: Adaptor, parents: Parents): Unit = {
    containerHead(adaptor)
    emitVitalDefinitionDetails(adaptor, parents)
    h2(s"Direction: ${adaptor.direction.format} ${adaptor.referent.format}")
    emitProcessorDetails[AdaptorContents](adaptor, parents)
  }
}
