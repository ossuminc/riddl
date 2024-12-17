/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.transforms

import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST.ParentStack
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable.ArrayBuffer

object FlattenPass extends PassInfo[PassOptions] {
  val name: String = "Flatten"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => FlattenPass(in, out)
  }
  
  case class Output(root: Root) extends PassOutput
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

  override protected def process(definition: RiddlValue, parents:  ParentStack): Unit =
    definition match
      case include @ Include(loc, _, contents) =>
        val parent: Parent = parents.head
        parent.contents.indexOf(include) match
          case -1 => // error
          case includeIndex: Int =>
            parent.contents.insertAll(includeIndex, contents.toIndexedSeq)
        end match     
        
        
    end match
  end process

  override def result(root:  Root): PassOutput = FlattenPass.Output(root)

end FlattenPass
