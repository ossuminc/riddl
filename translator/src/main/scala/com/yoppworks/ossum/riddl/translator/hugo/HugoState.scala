package com.yoppworks.ossum.riddl.translator.hugo

import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Folding

import scala.collection.mutable

/** Translation state for the Hugo Translator */
case class HugoState(
  config: HugoConfig,
  root: AST.RootContainer,
  contextStack: mutable.Stack[AST.Definition] = mutable.Stack[AST.Definition](),
  generatedFiles: mutable.ListBuffer[HugoFile] = mutable.ListBuffer.empty)
    extends Folding.State[HugoState] {

  def step(f: HugoState => HugoState): HugoState = f(this)

  def pushContext(definition: AST.Definition): Unit = {
    current match {
      case c: AST.Container =>
        require(c.contents.contains(definition), "illegal scoping")
        contextStack.push(definition)
      case d: AST.Definition =>
        throw new IllegalArgumentException(s"Definition ${d.identify} is not a container")
    }
  }

  def current: AST.Definition = contextStack.head

  def popContext(): Unit = { contextStack.pop() }

  def openFile(name: String): HugoFile = {
    val hFile = HugoFile(current, contextPath.resolve(name))
    generatedFiles.append(hFile)
    hFile
  }

  def contextPath: Path = {
    contextStack.map(x => x).foldRight(config.basePath) { case (elem, base) =>
      base.resolve(elem.id.value)
    }
  }

}
