package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassOutput

case class PrettifyOutput(
  messages: Messages = Messages.empty,
  state: PrettifyState
) extends PassOutput
