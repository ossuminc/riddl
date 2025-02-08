/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.transforms

import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST.ParentStack
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext

object FlattenPass extends PassInfo[PassOptions] {
  val name: String = "Flatten"
  def creator(options: PassOptions)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => FlattenPass(in, out)
  }
  case class Output(root: PassRoot, messages: Messages.Messages) extends PassOutput
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
    extends DepthFirstPass(input, outputs):

  override def name: String = FlattenPass.name

  override protected def process(definition: RiddlValue, parents: ParentStack): Unit =
    val parent: Branch[?] = parents.head
    definition match
      case include: Include[parent.ContentType] @unchecked =>
        parent.contents.indexOf(include) match
          case -1                => // error
          case includeIndex: Int =>
            val (prefix, suffix) = parent.contents.splitAt(includeIndex): @unchecked
            prefix.dropRight(1)
            val mid = prefix.merge(include.contents)
            mid.merge(suffix)
        end match
      case _ => ()
    end match
  end process

  override def result(root: PassRoot): PassOutput = FlattenPass.Output(root, messages.toMessages)

end FlattenPass
