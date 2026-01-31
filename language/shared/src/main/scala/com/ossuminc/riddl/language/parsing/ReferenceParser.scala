/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait ReferenceParser {
  this: CommonParser =>

  private def adaptorRef[u: P]: P[AdaptorRef] = {
    P(Index ~ Keywords.adaptor ~ pathIdentifier ~/ Index).map { case (start, pid, end) =>
      AdaptorRef(at(start, end), pid)
    }
  }

  private def commandRef[u: P]: P[CommandRef] = {
    P(Index ~ Keywords.command ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      CommandRef(at(start, end), pid)
    }
  }

  private def eventRef[u: P]: P[EventRef] = {
    P(Index ~ Keywords.event ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      EventRef(at(start, end), pid)
    }
  }

  private def queryRef[u: P]: P[QueryRef] = {
    P(Index ~ Keywords.query ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      QueryRef(at(start, end), pid)
    }
  }

  private def resultRef[u: P]: P[ResultRef] = {
    P(Index ~ Keywords.result ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      ResultRef(at(start, end), pid)
    }
  }

  private def recordRef[u: P]: P[RecordRef] = {
    P(Index ~ Keywords.record ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      RecordRef(at(start, end), pid)
    }
  }

  def messageRef[u: P]: P[MessageRef] = {
    P(commandRef | eventRef | queryRef | resultRef | recordRef)
  }

  def entityRef[u: P]: P[EntityRef] = {
    P(Index ~ Keywords.entity ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      EntityRef(at(start, end), pid)
    }
  }

  def functionRef[u: P]: P[FunctionRef] = {
    P(Index ~ Keywords.function ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      FunctionRef(at(start, end), pid)
    }
  }

  def handlerRef[u: P]: P[HandlerRef] = {
    P(Index ~ Keywords.handler ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      HandlerRef(at(start, end), pid)
    }
  }

  def stateRef[u: P]: P[StateRef] = {
    P(Index ~ Keywords.state ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      StateRef(at(start, end), pid)
    }
  }

  def typeRef[u: P]: P[TypeRef] = {
    P(
      Index ~ Keywords.typeKeywords.? ~ pathIdentifier ~ Index
    ).map {
      case (start, Some(key), pid, end) => TypeRef(at(start, end), key, pid)
      case (start, None, pid, end)      => TypeRef(at(start, end), "type", pid)
    }
  }

  def fieldRef[u: P]: P[FieldRef] = {
    P( Index ~ Keywords.field ~ pathIdentifier ~/ Index).map { case (start, pid, end) =>
      FieldRef(at(start, end), pid)
    }
  }

  def constantRef[u: P]: P[ConstantRef] = {
    P(Index ~ Keywords.constant ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      ConstantRef(at(start, end), pid)
    }
  }

  def contextRef[u: P]: P[ContextRef] = {
    P(Index ~ Keywords.context ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      ContextRef(at(start, end), pid)
    }
  }

  def outletRef[u: P]: P[OutletRef] = {
    P(Index ~ Keywords.outlet ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      OutletRef(at(start, end), pid)
    }
  }

  def inletRef[u: P]: P[InletRef] = {
    P(Index ~ Keywords.inlet ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      InletRef(at(start, end), pid)
    }
  }

  private def streamletRef[u: P]: P[StreamletRef] = {
    P(Index ~ Keywords.streamlets ~ pathIdentifier ~~ Index ).map { case (start, streamlets, pid, end) =>
      StreamletRef(at(start, end), streamlets, pid)
    }
  }

  private def projectorRef[u: P]: P[ProjectorRef] = {
    P(Index ~ Keywords.projector ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      ProjectorRef(at(start, end), pid)
    }
  }

  def repositoryRef[u: P]: P[RepositoryRef] = {
    P(Index ~ Keywords.repository ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      RepositoryRef(at(start, end), pid)
    }
  }

  def sagaRef[u: P]: P[SagaRef] = {
    P(Index ~ Keywords.saga ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      SagaRef(at(start, end), pid)
    }
  }

  def epicRef[u: P]: P[EpicRef] = {
    P(Index ~ Keywords.epic ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      EpicRef(at(start, end), pid)
    }
  }

  def userRef[u: P]: P[UserRef] = {
    P(Index ~ Keywords.user ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      UserRef(at(start, end), pid)
    }
  }

  def outputRef[u: P]: P[OutputRef] = {
    P(Index ~ outputAliases ~ pathIdentifier ~~ Index )
      .map { case (start, keyword, pid, end) => OutputRef(at(start, end), keyword, pid) }
  }

  def inputRef[u: P]: P[InputRef] = {
    P(Index ~ inputAliases ~ pathIdentifier ~~ Index )
      .map { case (start, keyword, pid, end) => InputRef(at(start, end), keyword, pid) }
  }

  def groupRef[u: P]: P[GroupRef] = {
    P(Index ~ groupAliases ~ pathIdentifier ~~ Index )
      .map { case (start, keyword, pid, end) => GroupRef(at(start, end), keyword, pid) }
  }

  def authorRef[u: P]: P[AuthorRef] = {
    P(Index ~ by ~ Keywords.author ~ pathIdentifier ~~ Index ).map { case (start, pid, end) =>
      AuthorRef(at(start, end), pid)
    }
  }

  def processorRef[u: P]: P[ProcessorRef[Processor[?]]] = {
    P(
      adaptorRef | contextRef | entityRef | projectorRef |
        repositoryRef | streamletRef
    )
  }

  private def arbitraryInteractionRef[u: P]: P[Reference[Definition]] = {
    P(processorRef | sagaRef | inputRef | outputRef | groupRef)
  }

  def anyInteractionRef[u: P]: P[Reference[Definition]] = {
    arbitraryInteractionRef | userRef
  }
}
