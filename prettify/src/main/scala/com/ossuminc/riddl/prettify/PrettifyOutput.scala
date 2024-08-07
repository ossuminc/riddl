package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassOutput

case class PrettifyOutput(
  root: Root = Root.empty,
  messages: Messages = Messages.empty,
  state: PrettifyState
) extends PassOutput
