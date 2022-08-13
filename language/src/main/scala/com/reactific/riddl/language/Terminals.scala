/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language

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
    final val equals = "="
    final val ellipsis = "..."
    final val ellipsisQuestion = "...?"
    final val plus = "+"
    final val question = "?"
    final val quote = "\""
    final val roundOpen = "("
    final val roundClose = ")"
    final val squareOpen = "["
    final val squareClose = "]"
    final val undefined = "???"
    final val verticalBar = "|"

    val all: Seq[String] = Seq(
      comma,
      colon,
      dot,
      equals,
      quote,
      quote,
      curlyOpen,
      curlyClose,
      squareOpen,
      squareClose,
      roundOpen,
      roundClose,
      undefined,
      verticalBar
    )
  }

  object Options {
    final val actor = "actor"
    final val aggregate = "aggregate"
    final val async = "async"
    final val available = "available"
    final val concept = "concept"
    final val consistent = "consistent"
    final val device = "device"
    final val eventSourced = "event sourced"
    final val function = "function"
    final val gateway = "gateway"
    final val kind = "kind"
    final val messageQueue = "mq"
    final val parallel = "parallel"
    final val reply = "reply"
    final val sequential = "sequential"
    final val service = "service"
    final val sync = "sync"
    final val value = "value"
    final val wrapper = "wrapper"
    final val stateMachine = "fsm"
    final val transient = "transient"
  }

  object Keywords {
    final val accepted = "accepted"
    final val action = "action"
    final val adapt = "adapt"
    final val adaptor = "adaptor"
    final val all = "all"
    final val any = "any"
    final val append = "append"
    final val ask = "ask"
    final val author = "author"
    final val background = "background"
    final val become = "become"
    final val benefit = "benefit"
    final val brief = "brief"
    final val briefly = "briefly"
    final val but = "but"
    final val call = "call"
    final val capability = "capability"
    final val causing = "causing"
    final val command = "command"
    final val commands = "commands"
    final val condition = "condition"
    final val consumer = "consumer"
    final val context = "context"
    final val create = "create"
    final val described = "described"
    final val details = "details"
    final val do_ = "do"
    final val domain = "domain"
    final val each = "each"
    final val else_ = "else"
    final val email = "email"
    final val entity = "entity"
    final val error = "error"
    final val event = "event"
    final val events = "events"
    final val example = "example"
    final val execute = "execute"
    final val explained = "explained"
    final val feature = "feature"
    final val file = "file"
    final val flow = "flow"
    final val function = "function"
    final val given_ = "given"
    final val handler = "handler"
    final val handles = "handles"
    final val implemented = "implemented"
    final val import_ = "import"
    final val include = "include"
    final val inlet = "inlet"
    final val input = "input"
    final val invariant = "invariant"
    final val items = "items"
    final val joint = "joint"
    final val many = "many"
    final val mapping = "mapping"
    final val merge = "merge"
    final val message = "message"
    final val morph = "morph"
    final val multi = "multi"
    final val name = "name"
    final val on = "on"
    final val one = "one"
    final val organization = "organization"
    final val option = "option"
    final val optional = "optional"
    final val options = "options"
    final val other = "other"
    final val outlet = "outlet"
    final val output = "output"
    final val pipe = "pipe"
    final val plant = "plant"
    final val processor = "processor"
    final val publish = "publish"
    final val query = "query"
    final val queries = "queries"
    final val range = "range"
    final val reference = "reference"
    final val remove = "remove"
    final val reply = "reply"
    final val requires = "requires"
    final val result = "result"
    final val results = "results"
    final val reverted = "reverted"
    final val role = "role"
    final val saga = "saga"
    final val scenario = "scenario"
    final val see = "see"
    final val set = "set"
    final val shown = "shown"
    final val sink = "sink"
    final val source = "source"
    final val split = "split"
    final val state = "state"
    final val step = "step"
    final val story = "story"
    final val tell = "tell"
    final val term = "term"
    final val then_ = "then"
    final val title = "title"
    final val topic = "topic"
    final val transform = "transform"
    final val transmit = "transmit"
    final val `type` = "type"
    final val url = "url"
    final val value = "value"
    final val when = "when"
    final val yields = "yields"
    final val yield_ = "yield"
  }

  object Predefined {
    final val Abstract = "Abstract"
    final val Boolean = "Boolean"
    final val Date = "Date"
    final val DateTime = "DateTime"
    final val Decimal = "Decimal"
    final val Duration = "Duration"
    final val Id = "Id"
    final val Integer = "Integer"
    final val LatLong = "LatLong"
    final val Nothing = "Nothing"
    final val Number = "Number"
    final val Pattern = "Pattern"
    final val Real = "Real"
    final val String = "String"
    final val Time = "Time"
    final val TimeStamp = "TimeStamp"
    final val UniqueId = "UniqueId"
    final val URL = "URL"
    final val UUID = "UUID"
  }

  object Readability {
    final val and = "and"
    final val are = "are"
    final val as = "as"
    final val by = "by"
    final val for_ = "for"
    final val from = "from"
    final val in = "in"
    final val is = "is"
    final val of = "of"
    final val on = "on"
    final val to = "to"
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
