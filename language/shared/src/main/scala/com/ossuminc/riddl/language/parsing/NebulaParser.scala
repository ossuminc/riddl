package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing production rules for Modules
  * {{{
  *   Root = Comment | Domain | Module | Author
  *   Module = Root | Context | User | Epic | Author | Application | Saga
  *   Domain = VitalDefinition | Domain | Context | User | Epic | Author | Application |  Saga
  * }}}
  */
private[parsing] trait NebulaParser {
  this: ProcessorParser & DomainParser & AdaptorParser & ApplicationParser & ContextParser & EntityParser & EpicParser &
    FunctionParser & HandlerParser & ProjectorParser & RepositoryParser & RootParser & SagaParser & StreamingParser &
    TypeParser & Readability & CommonParser =>

  private def nebulaContent[u:P]: P[NebulaContents] =
    P(adaptor | application | author | connector | constant | containedGroup | context | domain |
      entity | enumerator | epic | field | function | group | handler(StatementsSet.AllStatements) |
      inlet | input | invariant | method | module | onClause(StatementsSet.AllStatements)  | outlet | output |
      projector | relationship | repository | root | saga | sagaStep | schema | state | streamlet | term | typeDef |
      useCase | user)
    
  private def nebulaContents[u:P]: P[Seq[NebulaContents]] =
    P(nebulaContent).rep(0).asInstanceOf[P[Seq[NebulaContents]]]
    
  def nebula[u: P]: P[Nebula] = {
    P(Start ~ Keywords.nebula ~ is ~ open ~ nebulaContents ~ close ~ End).map { 
      (contents: Seq[NebulaContents]) => Nebula(contents) 
    }
  }
}
