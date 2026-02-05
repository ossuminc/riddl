/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.transforms

import com.ossuminc.riddl.language.{Messages, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext

object FlattenPass extends PassInfo[PassOptions]:
  val name: String = "Flatten"

  def creator(options: PassOptions)(using PlatformContext): PassCreator =
    (in: PassInput, out: PassesOutput) => FlattenPass(in, out)

  case class Output(
    root: PassRoot,
    messages: Messages.Messages
  ) extends PassOutput
end FlattenPass

/** A Pass for flattening the structure of a model.
  *
  * Removes all Include and BASTImport wrapper nodes from the AST,
  * promoting their children to the parent container. This produces
  * a flat AST where all definitions are direct children of their
  * logical parent.
  *
  * The pass uses a no-op process method; all work is done in
  * result() via the Container.flatten() extension method to avoid
  * modifying contents during traversal.
  *
  * @param input
  *   Input from previous passes
  * @param outputs
  *   The outputs from prior passes
  */
case class FlattenPass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs):

  override def name: String = FlattenPass.name

  override protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = ()

  override def result(root: PassRoot): PassOutput =
    root.flatten()
    FlattenPass.Output(root, messages.toMessages)

end FlattenPass
