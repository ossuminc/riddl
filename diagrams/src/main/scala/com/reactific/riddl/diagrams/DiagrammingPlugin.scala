package com.reactific.riddl.diagrams

import com.reactific.riddl.language.AST.*

trait DiagramMakerPlugin {

  def makeRootOverview(root: RootContainer, rootName: String): String
  def makeDomainOverview(root: RootContainer, domain: Domain): String
  def makeContextOverview(root: RootContainer, context: Context): String
  def makeEntityOverview(root: RootContainer, entity: Entity): String
  def makeStateDetail(root: RootContainer, state: State): String
  def makeStoryDiagram(root: RootContainer, story: Story): String
}
