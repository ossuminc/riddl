package com.yoppworks.ossum.riddl.translator

import com.yoppworks.ossum.riddl.parser.AST._

/** An F-Algebra based anamorphism for the AST */
object Translation {

  scala.languageFeature.higherKinds

  type Translator[A <: RiddlNode, B] = A => B

  type Container[F[_], A <: RiddlNode] = F[A]
}
