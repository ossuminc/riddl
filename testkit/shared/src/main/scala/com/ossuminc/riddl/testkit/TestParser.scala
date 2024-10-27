/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.{TestParser => LanguageTestParser}
import com.ossuminc.riddl.utils.PlatformContext

class TestParser(input: RiddlParserInput, throwOnError: Boolean)(using PlatformContext)
    extends LanguageTestParser(input, throwOnError)
