package com.yoppworks.ossum.riddl.language

/** Terminal symbol definitions in various categories */
object Terminals {

  object Punctuation {
    val asterisk = "*"
    val comma = ","
    val colon = ":"
    val curlyOpen = "{"
    val curlyClose = "}"
    val dot = "."
    val equals = "="
    val ellipsis = "..."
    val ellipsisQuestion = "...?"
    val plus = "+"
    val question = "?"
    val quote = "\""
    val roundOpen = "("
    val roundClose = ")"
    val squareOpen = "["
    val squareClose = "]"
    val undefined = "???"
    val verticalBar = "|"

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
    final val consistent = "consistent"
    final val device = "device"
    final val eventSourced = "event sourced"
    final val function = "function"
    final val gateway = "gateway"
    final val persistent = "persistent"
    final val reply = "reply"
    final val concept = "concept"
    final val sync = "sync"
    final val value = "value"
    final val wrapper = "wrapper"
  }

  object Keywords {
    final val action = "action"
    final val adaptor = "adaptor"
    final val all = "all"
    final val any = "any"
    final val append = "append"
    final val background = "background"
    final val brief = "brief"
    final val call = "call"
    final val causing = "causing"
    final val command = "command"
    final val commands = "commands"
    final val consumer = "handler"
    final val context = "context"
    final val described = "described"
    final val details = "details"
    final val domain = "domain"
    final val each = "each"
    final val else_ = "else"
    final val entity = "entity"
    final val event = "event"
    final val events = "events"
    final val example = "example"
    final val execute = "execute"
    final val explained = "explained"
    final val feature = "feature"
    final val function = "function"
    final val handler = "handler"
    final val handles = "handles"
    final val import_ = "import"
    final val include = "include"
    final val input = "input"
    final val interaction = "interaction"
    final val invariant = "invariant"
    final val items = "items"
    final val many = "many"
    final val mapping = "mapping"
    final val on = "on"
    final val one = "one"
    final val option = "option"
    final val optional = "optional"
    final val options = "options"
    final val output = "output"
    final val publish = "publish"
    final val query = "query"
    final val queries = "queries"
    final val range = "range"
    final val remove = "remove"
    final val requires = "requires"
    final val result = "result"
    final val results = "results"
    final val role = "role"
    final val see = "see"
    final val send = "send"
    final val set = "set"
    final val state = "state"
    final val then_ = "then"
    final val topic = "topic"
    final val `type` = "type"
    final val value = "value"
    final val when = "when"
    final val yields = "yields"
  }

  object Predefined {
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
  }

  object Readability {
    final val and = "and"
    final val are = "are"
    final val as = "as"
    final val by = "by"
    final val for_ = "for"
    final val from = "from"
    final val is = "is"
    final val of = "of"
    final val on = "on"
    final val to = "to"
  }

  object Operators {
    final val and = "and"
    final val or = "or"
    final val not = "not"
    final val plus = "+"
    final val minus = "-"
    final val times = "*"
    final val div = "/"
    final val mod = "%"
  }
}
