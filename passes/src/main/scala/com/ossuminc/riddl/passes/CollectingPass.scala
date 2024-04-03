package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.RiddlValue
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.symbols.Symbols.ParentStack

import scala.collection.mutable

/** An abstract PassOutput for use with passes that derive from CollectingPass. This just provides a standard field name
  * for the data that is collected, being `collected`.
  *
  * @param messages
  *   The required messages field from the PassOutput trait
  * @param collected
  *   The data that was collected from the CollectingPass's run
  * @tparam T
  *   The element type of the collected data
  */
abstract class CollectingPassOutput[T](
  messages: Messages = Messages.empty,
  collected: Seq[T] = Seq.empty[T]
) extends PassOutput

/** A Pass subclass that processes the AST exactly the same as the depth first search that the Pass class uses. The only
  * difference is that
  *
  * @param input
  *   The PassInput to process
  * @param outputs
  *   The outputs from previous pass runs in case they are needed as input to this CollectingPass
  * @tparam F
  *   The element type of the collected values
  */
abstract class CollectingPass[F](input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  /** The method usually called for each definition that is to be processed but our implementation of traverse instead
    * calls collect so a value can be returned. This implementation is final because it is meant to be ignored.
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[com.ossuminc.riddl.language.AST.Definition]]. This stack goes
    *   from immediate parent towards the root. The root is deepest in the stack.
    */
  override final def process(definition: RiddlValue, parents: ParentStack): Unit = {
    val collected: Seq[F] = collect(definition, parents)
    collectedValues ++= collected
  }

  protected val collectedValues: mutable.ArrayBuffer[F] = mutable.ArrayBuffer.empty[F]

  /** The processing method called at each node, similar to [[com.ossuminc.riddl.passes.Pass.process]] but modified to
    * return an sequence of the collectable, [[F]].
    *
    * @param definition
    *   The definition from which an [[F]] value is collected.
    * @param parents
    *   The parents of the definition
    * @return
    *   One of the collected values, an [[F]]
    */
  protected def collect(definition: RiddlValue, parents: ParentStack): Seq[F]

  override def result: CollectingPassOutput[F]
}