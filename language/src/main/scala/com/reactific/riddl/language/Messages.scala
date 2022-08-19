package com.reactific.riddl.language

object Messages {

  sealed trait KindOfMessage {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false
  }

  final case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"
  }

  final case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
  }

  final case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
  }

  final case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
  }

  final case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
  }

  case class Message(
    loc: Location,
    message: String,
    kind: KindOfMessage = Error)
      extends Ordered[Message] {

    def format: String = { s"$kind: $loc: $message" }

    override def compare(that: Message): Int = this.loc.compare(that.loc)
  }

  type Messages = List[Message]

  val empty: Messages = List.empty[Message]

}
