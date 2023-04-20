/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

/** Terminal symbol definitions in various categories */

object Terminals {

  object Punctuation {
    final val asterisk = "*"
    final val at = "@"
    final val comma = ","
    final val colon = ":"
    final val curlyOpen = "{"
    final val curlyClose = "}"
    final val dot = "."
    final val equalsSign = "="
    final val ellipsis = "..."
    final val ellipsisQuestion = "...?"
    final val exclamation = "!"
    final val plus = "+"
    final val question = "?"
    final val quote = "\""
    final val roundOpen = "("
    final val roundClose = ")"
    final val squareOpen = "["
    final val squareClose = "]"
    final val undefinedMark = "???"
    final val verticalBar = "|"

    val all: Seq[String] = Seq(
      comma,
      colon,
      dot,
      equalsSign,
      quote,
      quote,
      curlyOpen,
      curlyClose,
      squareOpen,
      squareClose,
      roundOpen,
      roundClose,
      undefinedMark,
      verticalBar
    )
  }

  object Options {
    final val aggregate = "aggregate"
    final val async = "async"
    final val available = "available"
    final val concept = "concept"
    final val consistent = "consistent"
    final val device = "device"
    final val eventSourced = "event-sourced"
    final val finiteStateMachine = "fsm"
    final val gateway = "gateway"
    final val kind = "kind"
    final val messageQueue = "mq"
    final val package_ = "package"
    final val parallel = "parallel"
    final val persistent = "persistent"
    final val reply = "reply"
    final val sequential = "sequential"
    final val service = "service"
    final val sync = "sync"
    final val value = "value"
    final val wrapper = "wrapper"
    final val tail_recursive = "tail-recursive"
    final val technology = "technology"
    final val transient = "transient"
    final val user = "user"
  }

  object Keywords {
    final val acquires = "acquires"
    final val user = "user"
    final val adaptor = "adaptor"
    final val all = "all"
    final val any = "any"
    final val append = "append"
    final val application = "application"
    final val arbitrary = "arbitrary"
    final val author = "author"
    final val become = "become"
    final val benefit = "benefit"
    final val brief = "brief"
    final val briefly = "briefly"
    final val but = "but"
    final val call = "call"
    final val case_ = "case"
    final val capability = "capability"
    final val command = "command"
    final val commands = "commands"
    final val condition = "condition"
    final val connector = "connector"
    final val const = "constant"
    final val container = "container"
    final val context = "context"
    final val create = "create"
    final val described = "described"
    final val design = "storyCase"
    final val details = "details"
    final val presents = "presents"
    final val do_ = "do"
    final val domain = "domain"
    final val each = "each"
    final val else_ = "else"
    final val email = "email"
    final val entity = "entity"
    final val epic = "epic"
    final val error = "error"
    final val event = "event"
    final val example = "example"
    final val execute = "execute"
    final val explained = "explained"
    final val fields = "fields"
    final val file = "file"
    final val flow = "flow"
    final val flows = "flows"
    final val form = "form"
    final val function = "function"
    final val given_ = "given"
    final val group = "group"
    final val handler = "handler"
    final val import_ = "import"
    final val include = "include"
    final val init = "init"
    final val inlet = "inlet"
    final val inlets = "inlets"
    final val input = "input"
    final val invariant = "invariant"
    final val items = "items"
    final val many = "many"
    final val mapping = "mapping"
    final val merge = "merge"
    final val message = "message"
    final val morph = "morph"
    final val name = "name"
    final val new_ = "new"
    final val on = "on"
    final val one = "one"
    final val organization = "organization"
    final val option = "option"
    final val optional = "optional"
    final val options = "options"
    final val other = "other"
    final val outlet = "outlet"
    final val outlets = "outlets"
    final val output = "output"
    final val parallel = "parallel"
    final val pipe = "pipe"
    final val plant = "plant"
    final val projector = "projector"
    final val query = "query"
    final val range = "range"
    final val reference = "reference"
    final val remove = "remove"
    final val replica = "replica"
    final val reply = "reply"
    final val repository = "repository"
    final val requires = "requires"
    final val required = "required"
    final val record = "record"
    final val result = "result"
    final val results = "results"
    final val return_ = "return"
    final val returns = "returns"
    final val reverted = "reverted"
    final val role = "role"
    final val router = "router"
    final val saga = "saga"
    final val scenario = "scenario"
    final val see = "see"
    final val send = "send"
    final val sequence = "sequence"
    final val set = "set"
    final val shown = "shown"
    final val sink = "sink"
    final val source = "source"
    final val split = "split"
    final val state = "state"
    final val step = "step"
    final val story = "story"
    final val streamlet = "streamlet"
    final val tell = "tell"
    final val term = "term"
    final val then_ = "then"
    final val title = "title"
    final val transmit = "transmit"
    final val `type` = "type"
    final val url = "url"
    final val value = "value"
    final val view = "view"
    final val void = "void"
    final val when = "when"
    final val yields = "yields"
  }

  object Predefined {
    final val Abstract = "Abstract"
    final val Boolean = "Boolean"
    final val Current = "Current" // in amperes
    final val Currency = "Currency" // for some nation
    final val Date = "Date"
    final val DateTime = "DateTime"
    final val Decimal = "Decimal"
    final val Duration = "Duration"
    final val Id = "Id"
    final val Integer = "Integer"
    final val Location = "Location"
    final val Length = "Length" // in meters
    final val Luminosity = "Luminosity" // in candelas
    final val Mass = "Mass" // in kilograms
    final val Mole = "Mole" // in mol (amount of substance)
    final val Nothing = "Nothing"
    final val Natural = "Natural"
    final val Number = "Number"
    final val Pattern = "Pattern"
    final val Range = "Range"
    final val Real = "Real"
    final val String = "String"
    final val Temperature = "Temperature" // in Kelvin
    final val Time = "Time"
    final val TimeStamp = "TimeStamp"
    final val URL = "URL"
    final val UUID = "UUID"
    final val Whole = "Whole"
  }

  object Readability {
    final val and = "and"
    final val are = "are"
    final val as = "as"
    final val at = "at"
    final val by = "by"
    final val for_ = "for"
    final val from = "from"
    final val in = "in"
    final val is = "is"
    final val of = "of"
    final val on = "on"
    final val so = "so"
    final val that = "that"
    final val to = "to"
    final val wants = "wants"
    final val with_ = "with"
  }

  object Operators {
    final val and = "and"
    final val if_ = "if"
    final val not = "not"
    final val or = "or"
    final val xor = "xor"
    final val plus = "+"
    final val minus = "-"
    final val times = "*"
    final val div = "/"
    final val mod = "%"
  }
}
