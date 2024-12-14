/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.transforms

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST.ParentStack
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext

object FlattenPass extends PassInfo[PassOptions] {
  val name: String = "Flatten"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => FlattenPass(in, out)
  }
}

/** A Pass for flattening the structure of a model
 * @param input
 *   Input from previous passes
 * @param outputs
 *   The outputs from prior passes (symbols & resolution)
 */
case class FlattenPass(
 input: PassInput,
 outputs: PassesOutput
)(using PlatformContext)
  extends Pass(input, outputs):

  override def name: String = FlattenPass.name

  override protected def process(definition: RiddlValue, parents:  ParentStack): Unit =
    definition match
      case Include(loc, _, contents) =>
        val parent = parents.head
        parent.contents.appendAll(contents.toSeq)
    end match    
  end process

  override def result(root:  Root): PassOutput = ???

end FlattenPass
