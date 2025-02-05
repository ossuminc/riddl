/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.utils.{CommonOptions, Logger, PlatformContext}

import java.nio.file.Path

/** Base class of all Passes that translate the AST to some other form.
  *
  * @param input
  *   The input to be translated
  * @param outputs
  *   The prior outputs from preceding passes
  */
abstract class TranslatingPass(input: PassInput, outputs: PassesOutput)(using PlatformContext)
    extends Pass(input, outputs)
