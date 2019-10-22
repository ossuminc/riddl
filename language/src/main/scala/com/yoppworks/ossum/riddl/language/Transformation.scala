package com.yoppworks.ossum.riddl.language

import cats.Applicative
import cats.Eval
import cats.Traverse
import com.yoppworks.ossum.riddl.language.AST._

import scala.collection.mutable

trait Foo[A <: RiddlNode]

trait Transformer[A <: RiddlNode, G[_]] extends Traverse[G] {}

object TransformerInstances {

  type Container[S] = mutable.Buffer[S]

  implicit val stringTransformer =
    new Transformer[RiddlNode, Container] {
      def transform(value: RiddlNode): String = ???

      override def traverse[G[_], A, B](fa: Container[A])(f: A => G[B])(
        implicit evidence$1: Applicative[G]
      ): G[Container[B]] = ???

      override def foldLeft[A, B](fa: Container[A], b: B)(f: (B, A) => B): B =
        ???

      override def foldRight[A, B](fa: Container[A], lb: Eval[B])(
        f: (A, Eval[B]) => Eval[B]
      ): Eval[B] = ???
    }

}

object Transformation {

  import cats.implicits._

}
