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
      verticalBar
    )
  }

  object Options {
    final val aggregate = "aggregate"
    final val async = "async"
    final val available = "available"
    final val consistent = "consistent"
    final val device = "device"
    final val function = "function"
    final val gateway = "gateway"
    final val persistent = "persistent"
    final val person = "person"
    final val reply = "reply"
    final val software = "software"
    final val sync = "sync"
    final val wrapper = "wrapper"
    final val all: Seq[String] = Seq(
      aggregate,
      async,
      available,
      consistent,
      device,
      function,
      gateway,
      persistent,
      person,
      reply,
      software
    ).sorted.distinct
  }

  object Keywords {
    final val adaptor = "adaptor"
    final val background = "background"
    final val call = "call"
    final val causing = "causing"
    final val command = "command"
    final val commands = "commands"
    final val consumes = "consumes"
    final val context = "context"
    final val description = "description"
    final val domain = "domain"
    final val entity = "entity"
    final val event = "event"
    final val events = "events"
    final val example = "example"
    final val feature = "feature"
    final val function = "function"
    final val handles = "handles"
    final val include = "include"
    final val interaction = "interaction"
    final val invariant = "invariant"
    final val mapping = "mapping"
    final val option = "option"
    final val options = "options"
    final val produces = "produces"
    final val query = "query"
    final val queries = "queries"
    final val range = "range"
    final val requires = "requires"
    final val result = "result"
    final val results = "results"
    final val role = "role"
    final val subdomain = "subdomain"
    final val topic = "topic"
    final val value = "value"
    final val all_keywords = Seq(
      adaptor,
      background,
      call,
      causing,
      command,
      commands,
      consumes,
      context,
      description,
      domain,
      entity,
      event,
      events,
      example,
      feature,
      function,
      handles,
      interaction,
      invariant,
      mapping,
      option,
      options,
      produces,
      query,
      queries,
      range,
      requires,
      result,
      results,
      role,
      subdomain,
      topic,
      value
    ).sorted.distinct
  }

  object Predefined {
    final val Boolean = "Boolean"
    final val Date = "Date"
    final val DateTime = "DateTime"
    final val Decimal = "Decimal"
    final val Id = "Id"
    final val Integer = "Integer"
    final val Number = "Number"
    final val Pattern = "Pattern"
    final val Real = "Real"
    final val String = "String"
    final val Time = "Time"
    final val TimeStamp = "TimeStamp"
    final val UniqueId = "UniqueId"
    final val URL = "URL"
    final val all = Seq(
      String,
      Number,
      Integer,
      Decimal,
      Real,
      Date,
      Time,
      DateTime,
      TimeStamp,
      URL,
      Pattern,
      UniqueId
    ).sorted.distinct
  }

  object Readability {
    final val and = "and"
    final val are = "are"
    final val as = "as"
    final val for_ = "for"
    final val is = "is"
    final val many = "many"
    final val of = "of"
    final val optional = "optional"
    final val then_ = "then"
    final val yields = "yields"

    val all: Seq[String] = Seq(
      and,
      are,
      as,
      for_,
      is,
      many,
      of,
      optional,
      then_,
      yields
    ).sorted.distinct
  }
}
