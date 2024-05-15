package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Definition, Include, LeafDefinition, RiddlValue}
import com.ossuminc.riddl.passes.symbols.Symbols.{ParentStack, Parents}

/** A Pass base class that allows the processing to be done based on containers, and calling these methods:
  *   - openContainer at the start of container's processing
  *   - processLeaf for any leaf nodes within the container
  *   - closeContainer after all the container's contents have been processed
  *
  * This kind of Pass allows the processing to follow the AST hierarchy so that container nodes can run before all their
  * content (openContainer) and also after all its content (closeContainer). This is necessary for passes that must
  * maintain the hierarchical structure of the AST model in their processing.
  *
  * @param input
  *   The PassInput to process
  */
abstract class HierarchyPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  /** not required in this kind of pass, final override it as a result
    *
    * @param definition
    *   The definition to be processed
    * @param parents
    *   The stack of definitions that are the parents of [[com.ossuminc.riddl.language.AST.Definition]]. This stack goes
    *   from immediate parent towards the root. The root is deepest in the stack.
    */
  override final def process(definition: RiddlValue, parents: ParentStack): Unit = ()

  /** Called by traverse when a new container is started Subclasses must implement this method.
    * @param definition
    *   The definition that was opened
    * @param parents
    *   The parents of the definition opened
    */
  protected def openContainer(definition: Definition, parents: Parents): Unit

  /** Called by traverse when a leaf node is encountered Subclasses must implement this method
    * @param definition
    *   The leaf definition that was found
    * @param parents
    *   THe parents of the leaf node
    */
  protected def processLeaf(definition: LeafDefinition, parents: Parents): Unit

  /** Process a non-definition, non-include, value
    *
    * @param value
    *   The value to be processed
    * @param parents
    *   The parent definitions of value
    */
  protected def processValue(value: RiddlValue, parents: Parents): Unit = ()

  /** Called by traverse after all leaf nodes of an opened node have been processed and the opened node is now being
    * closed. Subclasses must implement this method.
    * @param definition
    *   The opened node that now needs to be closed
    * @param parents
    *   THe parents of the node to be closed; should be the same as when it was opened
    */
  protected def closeContainer(definition: Definition, parents: Parents): Unit

  /** Redefine traverse to make the three calls
    *
    * @param definition
    *   The RiddlValue being considered
    * @param parents
    *   The definition parents of the value
    */
  override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case leaf: LeafDefinition =>
        processLeaf(leaf, parents.toSeq)
      case container: Definition =>
        openContainer(container, parents.toSeq)
        parents.push(container)
        container.contents.foreach {
          case leaf: LeafDefinition   => processLeaf(leaf, parents.toSeq)
          case definition: Definition => traverse(definition, parents)
          case include: Include[?]    => traverse(include, parents)
          case value: RiddlValue      => processValue(value, parents.toSeq)
        }
        parents.pop()
        closeContainer(container, parents.toSeq)
      case include: Include[?] =>
        include.contents.foreach { item => traverse(item, parents) }
      case value: RiddlValue => processValue(value, parents.toSeq)
    }
  }
}
