/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.Messages.Messages

/** Cross-platform result type for RiddlLib operations.
  *
  * Provides a structured result that works natively on all
  * platforms (JVM, JS, Native) and maps cleanly to TypeScript's
  * `RiddlResult<T>` interface.
  *
  * @tparam T The type of the success value
  */
sealed trait RiddlResult[+T]:

  /** Whether the operation succeeded. */
  def succeeded: Boolean

  /** The success value, if present. */
  def value: Option[T]

  /** Error messages from a failed operation. */
  def errors: Messages

  /** Convert to Either for Scala interop. */
  def toEither: Either[Messages, T]

  /** Map over the success value. */
  def map[U](f: T => U): RiddlResult[U]

  /** FlatMap over the success value. */
  def flatMap[U](f: T => RiddlResult[U]): RiddlResult[U]
end RiddlResult

object RiddlResult:

  case class Success[T](result: T) extends RiddlResult[T]:
    def succeeded: Boolean = true
    def value: Option[T] = Some(result)
    def errors: Messages = List.empty
    def toEither: Either[Messages, T] = Right(result)
    def map[U](f: T => U): RiddlResult[U] =
      Success(f(result))
    def flatMap[U](
      f: T => RiddlResult[U]
    ): RiddlResult[U] = f(result)
  end Success

  case class Failure(errors: Messages)
    extends RiddlResult[Nothing]:
    def succeeded: Boolean = false
    def value: Option[Nothing] = None
    def toEither: Either[Messages, Nothing] = Left(errors)
    def map[U](f: Nothing => U): RiddlResult[U] = this
    def flatMap[U](
      f: Nothing => RiddlResult[U]
    ): RiddlResult[U] = this
  end Failure

  /** Convert from Either. */
  def fromEither[T](
    e: Either[Messages, T]
  ): RiddlResult[T] =
    e match
      case Right(v) => Success(v)
      case Left(m)  => Failure(m)
    end match
  end fromEither
end RiddlResult
