/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

package object language:

  import com.ossuminc.riddl.language.AST.*
  import org.scalatest.enablers.Emptiness

  implicit def emptinessContents[CV <: RiddlValue]: Emptiness[Contents[CV]] =
    new Emptiness[Contents[CV]] {
      def isEmpty(contents: Contents[CV]): Boolean = contents.isEmpty
    }
  end emptinessContents

end language
