package com.yoppworks.ossum.riddl.parser

import cats.Applicative
import cats.Eval
import cats.Traverse
import com.yoppworks.ossum.riddl.parser.AST._

import scala.collection.mutable

trait Foo[A <: AST]

trait Transformer[A <: AST, G[_]] extends Traverse[G] {}

object TransformerInstances {

  type Container[S] = mutable.MutableList[S]

  implicit val stringTransformer =
    new Transformer[AST, Container] {
      def transform(value: AST): String = ???

      override def traverse[G[_], A, B](fa: Container[A])(f: A ⇒ G[B])(
        implicit evidence$1: Applicative[G]
      ): G[Container[B]] = ???

      override def foldLeft[A, B](fa: Container[A], b: B)(f: (B, A) ⇒ B): B =
        ???

      override def foldRight[A, B](fa: Container[A], lb: Eval[B])(
        f: (A, Eval[B]) ⇒ Eval[B]
      ): Eval[B] = ???
    }

}

object Transformation {

  import cats.implicits._

}
