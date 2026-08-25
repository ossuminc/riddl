/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import fastparse.*
import MultiLineWhitespace.*
import com.ossuminc.riddl.language.parsing.Keyword.*

import java.lang.Character.{isLetter, isWhitespace}

/** Keywords must not be followed by other program text so ensure this happens
  */
object Keywords {

  // A keyword must be followed by a non-identifier character (not a letter, digit, hyphen, or underscore)
  // This prevents "event" from matching in "event-sourced" or "type_id"
  private val nonKeywordChars = (c: Char) => !isLetter(c) && c != '-' && c != '_' && !c.isDigit

  // Succeeds if the next character (look ahead without consuming) is not an
  // identifier character. This is used with keywords to make sure the keyword
  // isn't followed by keyword
  //
  // `private[parsing]`, not `private`: `Readability.readable` (same package, different file)
  // reuses this exact boundary test for the twelve readability words (`to`, `as`, `by`, …), which
  // had no word boundary at all until 2026-08-15 -- `tell tourCompleted to …` silently swallowed
  // `to` as a prefix of `tourCompleted` and the `!to` guard in `boundMessageValue` misfired. See
  // `Readability.readable`'s doc comment for the fix's reasoning.
  private[parsing] def isNotKeywordChar[u: P]: P[Unit] = { CharPred(nonKeywordChars) | End }

  def keyword[u: P](key: String): P[Unit] = {
    P(key ~~ &(isNotKeywordChar))./
  }

  /** A keyword match that does NOT cut, for a rule that must be able to backtrack.
    *
    * [[keyword]] ends in `./`, which is right almost everywhere: once the keyword is seen the
    * parser is committed and the error points at the real problem. It is WRONG where the rule sits
    * in a position the grammar may have to abandon -- `comparison` tries `comparand ~ operator`
    * and relies on backtracking when no operator follows, so a cut inside a comparand arm turns
    * `set x to system.now` into "Expected one of (!= | < | <= | == | > | >=)" at the end of the
    * statement. `self` never hit this because SelfValue is not a Comparand; `system` is.
    */
  private[parsing] def keywordNoCut[u: P](key: String): P[Unit] = {
    P(key ~~ &(isNotKeywordChar))
  }

  def keywords[u: P, T](keywordsRule: P[T]): P[T] = {
    P(keywordsRule ~~ &(isNotKeywordChar))./
  }

  def streamlets[u: P]: P[String] = keywords(
    StringIn(
      Keyword.source,
      Keyword.sink,
      Keyword.merge,
      Keyword.split,
      Keyword.void
    ).!
  )

  def typeKeywords[u: P]: P[String] = keywords(
    StringIn(
      Keyword.type_,
      Keyword.command,
      Keyword.query,
      Keyword.event,
      Keyword.result,
      Keyword.record,
      Keyword.graph,
      Keyword.table
    ).!
  )

  def acquires[u: P]: P[Unit] = keyword(Keyword.acquires)

  def activate[u: P]: P[Unit] = keyword(Keyword.activate)

  def adaptor[u: P]: P[Unit] = keyword(Keyword.adaptor)

  def all[u: P]: P[Unit] = keyword(Keyword.all)

  def any[u: P]: P[Unit] = keyword(Keyword.any)

  def append[u: P]: P[Unit] = keyword(Keyword.append)

  def attachment[u: P]: P[Unit] = keyword(Keyword.attachment)

  def author[u: P]: P[Unit] = keyword(Keyword.author)

  def become[u: P]: P[Unit] = keyword(Keyword.become)

  def benefit[u: P]: P[Unit] = keyword(Keyword.benefit)

  def briefly[u: P]: P[Unit] = keywords(StringIn(Keyword.briefly, Keyword.brief))

  def body[u: P]: P[Unit] = keyword(Keyword.body)

  def call[u: P]: P[Unit] = keyword(Keyword.call)

  def case_[u: P]: P[Unit] = keyword(Keyword.case_)

  def capability[u: P]: P[Unit] = keyword(Keyword.capability)

  def command[u: P]: P[Unit] = keyword(Keyword.command)

  def commands[u: P]: P[Unit] = keyword(Keyword.commands)

  def condition[u: P]: P[Unit] = keyword(Keyword.condition)

  def connector[u: P]: P[Unit] = keyword(Keyword.connector)

  def constant[u: P]: P[Unit] = keyword(Keyword.constant)

  def container[u: P]: P[Unit] = keyword(Keyword.container)

  def contains[u: P]: P[Unit] = keyword(Keyword.contains)

  def context[u: P]: P[Unit] = keyword(Keyword.context)

  def copyright[u: P]: P[Unit] = keyword(Keyword.copyright)

  def correlation[u: P]: P[Unit] = keyword(Keyword.correlation)

  def create[u: P]: P[Unit] = keyword(Keyword.create)

  /** A70: the three-word phrase introducing a [[AST.Correlation]]'s mandatory timeout clause,
    * `times out after "30 days" { … }`.
    *
    * One combinator rather than three exported keywords, for the same reason `reverted by` is
    * spelled as a keyword plus a readability word: the words are particles of one phrase, not
    * independently meaningful. They ARE registered in [[anyKeyword]] so the tokenizer colours them,
    * but deliberately NOT in [[definitionKeywords]] — `out`, `after` and `times` are ordinary
    * English and must remain legal identifiers.
    */
  def timesOutAfter[u: P]: P[Unit] =
    P(keyword(Keyword.times) ~ keyword(Keyword.out) ~ keyword(Keyword.after))

  def default[u: P]: P[Unit] = keyword(Keyword.default_)

  def direct[u: P]: P[Unit] = keyword(Keyword.direct)

  def described[u: P]: P[Unit] = keywords(
    StringIn(Keyword.described, Keyword.explained, Keyword.description, Keyword.explanation)
  )

  def details[u: P]: P[Unit] = keyword(Keyword.details)

  def do_[u: P]: P[Unit] = keyword(Keyword.do_)

  def domain[u: P]: P[Unit] = keyword(Keyword.domain)

  def else_[u: P]: P[Unit] = keyword(Keyword.else_)

  def email[u: P]: P[Unit] = keyword(Keyword.email)

  def end_[u: P]: P[Unit] = keyword(Keyword.end_)

  def entity[u: P]: P[Unit] = keyword(Keyword.entity)

  def epic[u: P]: P[Unit] = keyword(Keyword.epic)

  def error[u: P]: P[Unit] = keyword(Keyword.error)

  def event[u: P]: P[Unit] = keyword(Keyword.event)

  def example[u: P]: P[Unit] = keyword(Keyword.example)

  def execute[u: P]: P[Unit] = keyword(Keyword.execute)

  def explained[u: P]: P[Unit] = keyword(Keyword.explained)

  def field[u: P]: P[Unit] = keyword(Keyword.field)

  def figma[u: P]: P[Unit] = keyword(Keyword.figma)

  def file[u: P]: P[Unit] = keyword(Keyword.file)

  def flow[u: P]: P[Unit] = keyword(Keyword.flow)

  def focus[u: P]: P[Unit] = keyword(Keyword.focus)

  def `for`[u: P]: P[Unit] = keyword(Keyword.for_)

  def foreach[u: P]: P[Unit] = keyword(Keyword.foreach)
  def forward[u: P]: P[Unit] = keyword(Keyword.forward)

  def from[u: P]: P[Unit] = keyword(Keyword.from)

  def function[u: P]: P[Unit] = keyword(Keyword.function)

  def get[u: P]: P[Unit] = keyword(Keyword.get)

  def graph[u: P]: P[Unit] = keyword(Keyword.graph)

  def group[u: P]: P[Unit] = keyword(Keyword.group)

  def handler[u: P]: P[Unit] = keyword(Keyword.handler)

  def `if`[u: P]: P[Unit] = keyword(Keyword.if_)

  def import_[u: P]: P[Unit] = keyword(Keyword.import_)

  def include[u: P]: P[Unit] = keyword(Keyword.include)

  def index[u: P]: P[Unit] = keyword(Keyword.index)

  def init[u: P]: P[Unit] = keyword(Keyword.init)

  def initial[u: P]: P[Unit] = keyword(Keyword.initial)

  def initiate[u: P]: P[Unit] = keyword(Keyword.initiate)

  /** Optional `initial` marker returning whether it was present. MUST NOT cut (unlike `keyword`):
    * it precedes alternatives that both allow it (state vs handler), so a mismatch has to backtrack
    * to try the other alternative.
    */
  def maybeInitial[u: P]: P[Boolean] =
    P((Keyword.initial ~~ &(isNotKeywordChar)).!.?).map(_.isDefined)

  def inlet[u: P]: P[Unit] = keyword(Keyword.inlet)

  def inlets[u: P]: P[Unit] = keyword(Keyword.inlets)

  def input[u: P]: P[Unit] = keyword(Keyword.input)

  def invariant[u: P]: P[Unit] = keyword(Keyword.invariant)

  def items[u: P]: P[Unit] = keyword(Keyword.items)

  def label[u: P]: P[Unit] = keyword(Keyword.label)

  def let[u: P]: P[Unit] = keyword(Keyword.let)

  def link[u: P]: P[Unit] = keyword(Keyword.link)

  def many[u: P]: P[Unit] = keyword(Keyword.many)

  def mapping[u: P]: P[Unit] = keyword(Keyword.mapping)

  def `match`[u: P]: P[Unit] = keyword(Keyword.match_)

  def merge[u: P]: P[Unit] = keyword(Keyword.merge)

  def message[u: P]: P[Unit] = keyword(Keyword.message)

  def module[u: P]: P[Unit] = keyword(Keyword.module)

  def morph[u: P]: P[Unit] = keyword(Keyword.morph)

  def name[u: P]: P[Unit] = keyword(Keyword.name)

  def nebula[u: P]: P[Unit] = keyword(Keyword.nebula)

  def node[u: P]: P[Unit] = keyword(Keyword.node)

  def on[u: P]: P[Unit] = keyword(Keyword.on)

  def onActivate[u: P]: P[Unit] = keyword("on activate")

  def onInit[u: P]: P[Unit] = keyword("on init")

  def onOther[u: P]: P[Unit] = keyword("on other")

  def onPassivate[u: P]: P[Unit] = keyword("on passivate")

  def onTerm[u: P]: P[Unit] = keyword("on term")

  def one[u: P]: P[Unit] = keyword(Keyword.one)

  def option[u: P]: P[Unit] = keyword(Keyword.option)

  def optional[u: P]: P[Unit] = keyword(Keyword.optional)

  def options[u: P]: P[Unit] = keyword(Keyword.options)

  def or[u: P]: P[Unit] = keyword(Keyword.or)

  def organization[u: P]: P[Unit] = keyword(Keyword.organization)

  def other[u: P]: P[Unit] = keyword(Keyword.other)

  def outlet[u: P]: P[Unit] = keyword(Keyword.outlet)

  def outlets[u: P]: P[Unit] = keyword(Keyword.outlets)

  def output[u: P]: P[Unit] = keyword(Keyword.output)

  def parallel[u: P]: P[Unit] = keyword(Keyword.parallel)

  def passivate[u: P]: P[Unit] = keyword(Keyword.passivate)

  def pipe[u: P]: P[Unit] = keyword(Keyword.pipe)

  def plant[u: P]: P[Unit] = keyword(Keyword.plant)

  def presents[u: P]: P[Unit] = keyword(Keyword.presents)

  def processor[u: P]: P[Unit] = keyword(Keyword.processor)

  def projector[u: P]: P[Unit] = keyword(Keyword.projector)

  def prompt[u: P]: P[Unit] = keyword(Keyword.prompt)

  def put[u: P]: P[Unit] = keyword(Keyword.put)

  def query[u: P]: P[Unit] = keyword(Keyword.query)

  def range[u: P]: P[Unit] = keyword(Keyword.range)

  def reference[u: P]: P[Unit] = keyword(Keyword.reference)

  def relationship[u: P]: P[Unit] = keyword(Keyword.relationship)

  def remove[u: P]: P[Unit] = keyword(Keyword.remove)

  def replica[u: P]: P[Unit] = keyword(Keyword.replica)

  def ask[u: P]: P[Unit] = keyword(Keyword.ask)
  def replies[u: P]: P[Unit] = keyword(Keyword.replies)
  def reply[u: P]: P[Unit] = keyword(Keyword.reply)

  def repository[u: P]: P[Unit] = keyword(Keyword.repository)

  def require[u: P]: P[Unit] = keyword(Keyword.require_)

  def requires[u: P]: P[Unit] = keyword(Keyword.requires)

  def required[u: P]: P[Unit] = keyword(Keyword.required)

  def record[u: P]: P[Unit] = keyword(Keyword.record)

  def result[u: P]: P[Unit] = keyword(Keyword.result)

  def results[u: P]: P[Unit] = keyword(Keyword.results)

  def `return`[u: P]: P[Unit] = keyword(Keyword.return_)

  def returns[u: P]: P[Unit] = keyword(Keyword.returns)

  def reverted[u: P]: P[Unit] = keyword(Keyword.reverted)

  def router[u: P]: P[Unit] = keyword(Keyword.router)

  def refuses[u: P]: P[Unit] = keyword(Keyword.refuses)

  def saga[u: P]: P[Unit] = keyword(Keyword.saga)

  def schema[u: P]: P[Unit] = keywords(Keyword.schema)

  def self[u: P]: P[Unit] = keyword(Keyword.self)
  def system[u: P]: P[Unit] = keywordNoCut(Keyword.system)

  def selects[u: P]: P[Unit] = keyword(Keyword.selects)

  def send[u: P]: P[Unit] = keyword(Keyword.send)

  def sequence[u: P]: P[Unit] = keyword(Keyword.sequence)

  def set[u: P]: P[Unit] = keyword(Keyword.set)

  def show[u: P]: P[Unit] = keyword(Keyword.show)

  def shown[u: P]: P[Unit] = keyword(Keyword.shown)

  def sink[u: P]: P[Unit] = keyword(Keyword.sink)

  def source[u: P]: P[Unit] = keyword(Keyword.source)

  def split[u: P]: P[Unit] = keyword(Keyword.split)

  def state[u: P]: P[Unit] = keyword(Keyword.state)

  def step[u: P]: P[Unit] = keyword(Keyword.step)

  def stop[u: P]: P[Unit] = keyword(Keyword.stop)

  def story[u: P]: P[Unit] = keyword(Keyword.story)

  def streamlet[u: P]: P[Unit] = keyword(Keyword.streamlet)

  def table[u: P]: P[Unit] = keyword(Keyword.table)

  def take[u: P]: P[Unit] = keyword(Keyword.take)

  def tell[u: P]: P[Unit] = keyword(Keyword.tell)

  def term[u: P]: P[Unit] = keyword(Keyword.term)

  def terminate[u: P]: P[Unit] = keyword(Keyword.terminate)

  def `then`[u: P]: P[Unit] = keyword(Keyword.then_)

  def title[u: P]: P[Unit] = keyword(Keyword.title)

  def `type`[u: P]: P[Unit] = keyword(Keyword.type_)

  def url[u: P]: P[Unit] = keyword(Keyword.url)

  def updates[u: P]: P[Unit] = keyword(Keyword.updates)

  def user[u: P]: P[Unit] = keyword(Keyword.user)

  def value[u: P]: P[Unit] = keyword(Keyword.value)

  def version[u: P]: P[Unit] = keyword(Keyword.version)

  def void[u: P]: P[Unit] = keyword(Keyword.void)

  def when[u: P]: P[Unit] = keyword(Keyword.when)

  def where[u: P]: P[Unit] = keyword(Keyword.where)

  def `with`[u: P]: P[Unit] = keyword(Keyword.with_)

  def yields[u: P]: P[Unit] = keyword(Keyword.yields)

  def `yield`[u: P]: P[Unit] = keyword(Keyword.yield_)

  def anyKeyword[u: P]: P[Unit] = {
    P(
      keywords(
        StringIn(
          Keyword.acquires,
          Keyword.activate,
          Keyword.adaptor,
          Keyword.after,
          Keyword.all,
          Keyword.any,
          Keyword.append,
          Keyword.attachment,
          Keyword.author,
          Keyword.become,
          Keyword.benefit,
          Keyword.briefly,
          Keyword.body,
          Keyword.call,
          Keyword.case_,
          Keyword.capability,
          Keyword.command,
          Keyword.commands,
          Keyword.condition,
          Keyword.connector,
          Keyword.constant,
          Keyword.container,
          Keyword.contains,
          Keyword.context,
          Keyword.copyright,
          Keyword.correlation,
          Keyword.create,
          Keyword.described,
          Keyword.details,
          Keyword.direct,
          Keyword.presents,
          Keyword.do_,
          Keyword.domain,
          Keyword.else_,
          Keyword.email,
          Keyword.end_,
          Keyword.entity,
          Keyword.epic,
          Keyword.error,
          Keyword.event,
          Keyword.example,
          Keyword.execute,
          Keyword.explained,
          Keyword.field,
          Keyword.file,
          Keyword.flow,
          Keyword.focus,
          Keyword.for_,
          Keyword.foreach,
          Keyword.from,
          Keyword.function,
          Keyword.get,
          Keyword.graph,
          Keyword.group,
          Keyword.handler,
          Keyword.if_,
          Keyword.import_,
          Keyword.include,
          Keyword.index,
          Keyword.init,
          Keyword.initial,
          Keyword.inlet,
          Keyword.inlets,
          Keyword.input,
          Keyword.invariant,
          Keyword.items,
          Keyword.label,
          Keyword.link,
          Keyword.many,
          Keyword.mapping,
          Keyword.merge,
          Keyword.message,
          Keyword.module,
          Keyword.morph,
          Keyword.name,
          Keyword.nebula,
          Keyword.on,
          Keyword.one,
          Keyword.organization,
          Keyword.option,
          Keyword.optional,
          Keyword.options,
          Keyword.other,
          Keyword.out,
          Keyword.outlet,
          Keyword.outlets,
          Keyword.output,
          Keyword.parallel,
          Keyword.passivate,
          Keyword.pipe,
          Keyword.plant,
          Keyword.processor,
          Keyword.projector,
          Keyword.put,
          Keyword.query,
          Keyword.range,
          Keyword.reference,
          Keyword.relationship,
          Keyword.remove,
          Keyword.replica,
          Keyword.ask,
          Keyword.replies,
          Keyword.reply,
          Keyword.repository,
          Keyword.require_,
          Keyword.requires,
          Keyword.required,
          Keyword.record,
          Keyword.refuses,
          Keyword.result,
          Keyword.results,
          Keyword.return_,
          Keyword.returns,
          Keyword.reverted,
          Keyword.router,
          Keyword.saga,
          Keyword.schema,
          Keyword.selects,
          Keyword.send,
          Keyword.sequence,
          Keyword.set,
          Keyword.show,
          Keyword.shown,
          Keyword.sink,
          Keyword.source,
          Keyword.split,
          Keyword.state,
          Keyword.step,
          Keyword.stop,
          Keyword.story,
          Keyword.streamlet,
          Keyword.table,
          Keyword.take,
          Keyword.tell,
          Keyword.term,
          Keyword.then_,
          Keyword.times,
          Keyword.title,
          Keyword.type_,
          Keyword.url,
          Keyword.updates,
          Keyword.user,
          Keyword.value,
          Keyword.version,
          Keyword.void,
          Keyword.when,
          Keyword.where,
          Keyword.with_,
          Keyword.yields,
          Keyword.yield_
        )
      )
    )
  }
}

object Keyword {
  final val acquires = "acquires"
  final val activate = "activate"
  final val adaptor = "adaptor"
  final val after = "after"
  final val all = "all"
  final val any = "any"
  final val append = "append"
  final val attachment = "attachment"
  final val author = "author"
  final val become = "become"
  final val benefit = "benefit"
  final val brief = "brief"
  final val briefly = "briefly"
  final val body = "body"
  final val call = "call"
  final val case_ = "case"
  final val capability = "capability"
  final val command = "command"
  final val commands = "commands"
  final val condition = "condition"
  final val connector = "connector"
  final val constant = "constant"
  final val container = "container"
  final val contains = "contains"
  final val context = "context"
  final val copyright = "copyright"
  final val correlation = "correlation"
  final val create = "create"
  final val default_ = "default"
  final val described = "described"
  final val description = "description"
  final val details = "details"
  final val direct = "direct"
  final val presents = "presents"
  final val do_ = "do"
  final val domain = "domain"
  final val else_ = "else"
  final val email = "email"
  final val end_ = "end"
  final val entity = "entity"
  final val epic = "epic"
  final val error = "error"
  final val event = "event"
  final val example = "example"
  final val execute = "execute"
  final val explained = "explained"
  final val explanation = "explanation"
  final val field = "field"
  final val figma = "figma"
  final val file = "file"
  final val flow = "flow"
  final val focus = "focus"
  final val for_ = "for"
  final val foreach = "foreach"
  final val forward = "forward"
  final val form = "form"
  final val from = "from"
  final val fully = "fully"
  final val function = "function"
  final val get = "get"
  final val graph = "graph"
  final val group = "group"
  final val handler = "handler"
  final val if_ = "if"
  final val import_ = "import"
  final val include = "include"
  final val index = "index"
  final val init = "init"
  final val initial = "initial"
  final val initiate = "initiate"
  final val inlet = "inlet"
  final val inlets = "inlets"
  final val input = "input"
  final val invariant = "invariant"
  final val items = "items"
  final val label = "label"
  final val let = "let"
  final val link = "link"
  final val many = "many"
  final val match_ = "match"
  final val mapping = "mapping"
  final val merge = "merge"
  final val message = "message"
  final val module = "module"
  final val morph = "morph"
  final val name = "name"
  final val nebula = "nebula"
  final val node = "node"
  final val on = "on"
  final val one = "one"
  final val or = "or"
  final val organization = "organization"
  final val option = "option"
  final val optional = "optional"
  final val options = "options"
  final val other = "other"
  final val out = "out"
  final val outlet = "outlet"
  final val outlets = "outlets"
  final val output = "output"
  final val parallel = "parallel"
  final val passivate = "passivate"
  final val pipe = "pipe"
  final val plant = "plant"
  final val processor = "processor"
  final val projector = "projector"
  final val prompt = "prompt"
  final val put = "put"
  final val query = "query"
  final val range = "range"
  final val reference = "reference"
  final val relationship = "relationship"
  final val remove = "remove"
  final val replica = "replica"
  final val ask = "ask"
  final val replies = "replies"
  final val reply = "reply"
  final val repository = "repository"
  final val require_ = "require"
  final val requires = "requires"
  final val required = "required"
  final val record = "record"
  final val refuses = "refuses"
  final val result = "result"
  final val results = "results"
  final val return_ = "return"
  final val returns = "returns"
  final val reverted = "reverted"
  final val router = "router"
  final val saga = "saga"
  final val schema = "schema"
  final val self = "self"
  final val system = "system"
  final val selects = "selects"
  final val send = "send"
  final val sequence = "sequence"
  final val set = "set"
  final val show = "show"
  final val shown = "shown"
  final val sink = "sink"
  final val source = "source"
  final val split = "split"
  final val state = "state"
  final val step = "step"
  final val stop = "stop"
  final val story = "story"
  final val streamlet = "streamlet"
  final val table = "table"
  final val take = "take"
  final val tell = "tell"
  final val term = "term"
  final val terminate = "terminate"
  final val then_ = "then"
  final val times = "times"
  final val title = "title"
  final val type_ = "type"
  final val url = "url"
  final val updates = "updates"
  final val user = "user"
  final val value = "final value"
  final val version = "version"
  final val void = "void"
  final val when = "when"
  final val where = "where"
  final val with_ = "with"
  final val yields = "yields"
  final val yield_ = "yield"

  def allKeywords: Seq[String] = Seq(
    acquires,
    get,
    put,
    refuses,
    require_,
    activate,
    adaptor,
    after,
    all,
    any,
    append,
    attachment,
    correlation,
    times,
    out,
    author,
    become,
    benefit,
    brief,
    briefly,
    body,
    call,
    case_,
    capability,
    command,
    commands,
    condition,
    connector,
    constant,
    container,
    contains,
    context,
    copyright,
    create,
    default_,
    described,
    description,
    details,
    direct,
    presents,
    do_,
    domain,
    else_,
    email,
    end_,
    entity,
    epic,
    error,
    event,
    example,
    execute,
    explanation,
    explained,
    field,
    figma,
    file,
    flow,
    focus,
    for_,
    foreach,
    forward,
    form,
    from,
    fully,
    function,
    graph,
    group,
    handler,
    if_,
    import_,
    include,
    index,
    init,
    initial,
    inlet,
    inlets,
    input,
    invariant,
    items,
    label,
    let,
    link,
    many,
    mapping,
    match_,
    merge,
    message,
    module,
    morph,
    name,
    nebula,
    node,
    on,
    one,
    or,
    organization,
    option,
    optional,
    options,
    other,
    outlet,
    outlets,
    output,
    parallel,
    passivate,
    pipe,
    plant,
    processor,
    projector,
    prompt,
    query,
    range,
    reference,
    relationship,
    remove,
    replica,
    ask,
    replies,
    reply,
    repository,
    requires,
    required,
    record,
    result,
    results,
    return_,
    returns,
    reverted,
    router,
    saga,
    schema,
    self,
    selects,
    send,
    sequence,
    set,
    show,
    shown,
    sink,
    source,
    split,
    state,
    step,
    stop,
    story,
    streamlet,
    table,
    take,
    tell,
    term,
    then_,
    title,
    type_,
    url,
    updates,
    user,
    value,
    version,
    void,
    when,
    where,
    with_,
    yields,
    yield_
  )

  lazy val allKeywordsSet: Set[String] = allKeywords.toSet

  /** The keywords that INTRODUCE a definition, which an identifier may therefore not be spelled as:
    * `domain domain is { … }` used to parse, which reads as nonsense and is ambiguous to tooling.
    *
    * Deliberately NOT all keywords. `version` and `copyright` are keywords that models legitimately
    * use as field and type names, and A53/A47 kept them usable on purpose — see VersionTest and
    * CopyrightTest, which pin that. The same goes for type-ish words such as `table`, `graph` and
    * `result`. Banning every keyword breaks those models for no gain: the ambiguity only bites
    * where the word would otherwise start a definition.
    *
    * Case-SENSITIVE, since keywords are lower case: `Domain` is still a fine identifier, and only
    * the exact keyword spelling is refused. A quoted identifier ('domain') remains the escape
    * hatch.
    */
  lazy val definitionKeywords: Set[String] = Set(
    domain,
    context,
    entity,
    adaptor,
    saga,
    epic,
    projector,
    repository,
    streamlet,
    handler,
    function,
    // A70. `times`, `out` and `after` deliberately do NOT join this set even though they are
    // keywords: they are particles of the `times out after` phrase, they never introduce a
    // definition, and they are ordinary English words a model may legitimately want as a name.
    correlation
  )

}
