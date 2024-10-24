package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.parsing.{TestParser => LanguageTestParser}
import com.ossuminc.riddl.utils.PlatformIOContext

class TestParser(input: RiddlParserInput, throwOnError: Boolean)(using PlatformIOContext)
    extends LanguageTestParser(input, throwOnError)
