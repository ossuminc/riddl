package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.AdaptorDef
import fastparse._
import ScalaWhitespace._

/** Parser rules for Adaptors */
trait AdaptorParser extends CommonParser {

  def adaptorDef[_: P]: P[AdaptorDef] = {
    P(
      location ~ "adaptor" ~/ identifier ~ "for" ~/ domainRef.? ~/ contextRef ~
        open ~ close ~ addendum
    ).map { tpl =>
      (AdaptorDef.apply _).tupled(tpl)
    }
  }

}
