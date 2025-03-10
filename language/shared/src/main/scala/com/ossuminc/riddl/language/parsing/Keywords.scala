/*
 * Copyright 2019-2025 Ossum, Inc.
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

  private val nonKeywordChars = (c: Char) => !isLetter(c)

  // Succeeds if the next character (look ahead without consuming) is not an
  // identifier character. This is used with keywords to make sure the keyword
  // isn't followed by keyword
  private def isNotKeywordChar[u: P]: P[Unit] = { CharPred(nonKeywordChars) | End }

  def keyword[u: P](key: String): P[Unit] = {
    P(key ~~ &(isNotKeywordChar))./
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

  def create[u: P]: P[Unit] = keyword(Keyword.create)

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

  def file[u: P]: P[Unit] = keyword(Keyword.file)

  def flow[u: P]: P[Unit] = keyword(Keyword.flow)

  def focus[u: P]: P[Unit] = keyword(Keyword.focus)

  def `for`[u: P]: P[Unit] = keyword(Keyword.for_)

  def foreach[u: P]: P[Unit] = keyword(Keyword.foreach)

  def from[u: P]: P[Unit] = keyword(Keyword.from)

  def function[u: P]: P[Unit] = keyword(Keyword.function)

  def graph[u: P]: P[Unit] = keyword(Keyword.graph)

  def group[u: P]: P[Unit] = keyword(Keyword.group)

  def handler[u: P]: P[Unit] = keyword(Keyword.handler)

  def `if`[u: P]: P[Unit] = keyword(Keyword.if_)

  def import_[u: P]: P[Unit] = keyword(Keyword.import_)

  def include[u: P]: P[Unit] = keyword(Keyword.include)

  def index[u: P]: P[Unit] = keyword(Keyword.index)

  def init[u: P]: P[Unit] = keyword(Keyword.init)

  def inlet[u: P]: P[Unit] = keyword(Keyword.inlet)

  def inlets[u: P]: P[Unit] = keyword(Keyword.inlets)

  def input[u: P]: P[Unit] = keyword(Keyword.input)

  def invariant[u: P]: P[Unit] = keyword(Keyword.invariant)

  def items[u: P]: P[Unit] = keyword(Keyword.items)

  def label[u: P]: P[Unit] = keyword(Keyword.label)

  def link[u: P]: P[Unit] = keyword(Keyword.link)

  def many[u: P]: P[Unit] = keyword(Keyword.many)

  def mapping[u: P]: P[Unit] = keyword(Keyword.mapping)

  def merge[u: P]: P[Unit] = keyword(Keyword.merge)

  def message[u: P]: P[Unit] = keyword(Keyword.message)

  def module[u: P]: P[Unit] = keyword(Keyword.module)

  def morph[u: P]: P[Unit] = keyword(Keyword.morph)

  def name[u: P]: P[Unit] = keyword(Keyword.name)

  def nebula[u: P]: P[Unit] = keyword(Keyword.nebula)

  def on[u: P]: P[Unit] = keyword(Keyword.on)
  
  def onInit[u: P]: P[Unit] = keyword("on init")

  def onOther[u: P]: P[Unit] = keyword("on other")

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

  def pipe[u: P]: P[Unit] = keyword(Keyword.pipe)

  def plant[u: P]: P[Unit] = keyword(Keyword.plant)

  def presents[u: P]: P[Unit] = keyword(Keyword.presents)

  def projector[u: P]: P[Unit] = keyword(Keyword.projector)

  def query[u: P]: P[Unit] = keyword(Keyword.query)

  def range[u: P]: P[Unit] = keyword(Keyword.range)

  def reference[u: P]: P[Unit] = keyword(Keyword.reference)

  def relationship[u: P]: P[Unit] = keyword(Keyword.relationship)

  def remove[u: P]: P[Unit] = keyword(Keyword.remove)

  def replica[u: P]: P[Unit] = keyword(Keyword.replica)

  def reply[u: P]: P[Unit] = keyword(Keyword.reply)

  def repository[u: P]: P[Unit] = keyword(Keyword.repository)

  def requires[u: P]: P[Unit] = keyword(Keyword.requires)

  def required[u: P]: P[Unit] = keyword(Keyword.required)

  def record[u: P]: P[Unit] = keyword(Keyword.record)

  def result[u: P]: P[Unit] = keyword(Keyword.result)

  def results[u: P]: P[Unit] = keyword(Keyword.results)

  def `return`[u: P]: P[Unit] = keyword(Keyword.return_)

  def returns[u: P]: P[Unit] = keyword(Keyword.returns)

  def reverted[u: P]: P[Unit] = keyword(Keyword.reverted)

  def router[u: P]: P[Unit] = keyword(Keyword.router)

  def saga[u: P]: P[Unit] = keyword(Keyword.saga)

  def schema[u: P]: P[Unit] = keywords(Keyword.schema)

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

  def `then`[u: P]: P[Unit] = keyword(Keyword.then_)

  def title[u: P]: P[Unit] = keyword(Keyword.title)

  def `type`[u: P]: P[Unit] = keyword(Keyword.type_)

  def url[u: P]: P[Unit] = keyword(Keyword.url)

  def updates[u: P]: P[Unit] = keyword(Keyword.updates)

  def user[u: P]: P[Unit] = keyword(Keyword.user)

  def value[u: P]: P[Unit] = keyword(Keyword.value)

  def void[u: P]: P[Unit] = keyword(Keyword.void)

  def when[u: P]: P[Unit] = keyword(Keyword.when)

  def where[u: P]: P[Unit] = keyword(Keyword.where)

  def `with`[u: P]: P[Unit] = keyword(Keyword.with_)

  def anyKeyword[u: P]: P[Unit] = {
    P(
      keywords(
        StringIn(
          Keyword.acquires,
          Keyword.adaptor,
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
          Keyword.graph,
          Keyword.group,
          Keyword.handler,
          Keyword.if_,
          Keyword.import_,
          Keyword.include,
          Keyword.index,
          Keyword.init,
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
          Keyword.outlet,
          Keyword.outlets,
          Keyword.output,
          Keyword.parallel,
          Keyword.pipe,
          Keyword.plant,
          Keyword.projector,
          Keyword.query,
          Keyword.range,
          Keyword.reference,
          Keyword.relationship,
          Keyword.remove,
          Keyword.replica,
          Keyword.reply,
          Keyword.repository,
          Keyword.requires,
          Keyword.required,
          Keyword.record,
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
          Keyword.title,
          Keyword.type_,
          Keyword.url,
          Keyword.updates,
          Keyword.user,
          Keyword.value,
          Keyword.void,
          Keyword.when,
          Keyword.where,
          Keyword.with_
        )
      )
    )
  }
}

object Keyword {
  final val acquires = "acquires"
  final val adaptor = "adaptor"
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
  final val create = "create"
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
  final val file = "file"
  final val flow = "flow"
  final val focus = "focus"
  final val for_ = "for"
  final val foreach = "foreach"
  final val form = "form"
  final val from = "from"
  final val fully = "fully"
  final val function = "function"
  final val graph = "graph"
  final val group = "group"
  final val handler = "handler"
  final val if_ = "if"
  final val import_ = "import"
  final val include = "include"
  final val index = "index"
  final val init = "init"
  final val inlet = "inlet"
  final val inlets = "inlets"
  final val input = "input"
  final val invariant = "invariant"
  final val items = "items"
  final val label = "label"
  final val link = "link"
  final val many = "many"
  final val mapping = "mapping"
  final val merge = "merge"
  final val message = "message"
  final val module = "module"
  final val morph = "morph"
  final val name = "name"
  final val nebula = "nebula"
  final val on = "on"
  final val one = "one"
  final val or = "or"
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
  final val relationship = "relationship"
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
  final val router = "router"
  final val saga = "saga"
  final val schema = "schema"
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
  final val then_ = "then"
  final val title = "title"
  final val type_ = "type"
  final val url = "url"
  final val updates = "updates"
  final val user = "user"
  final val value = "final value"
  final val void = "void"
  final val when = "when"
  final val where = "where"
  final val with_ = "with"

  def allKeywords: Seq[String] = Seq(
    acquires,
    adaptor,
    all,
    any,
    append,
    attachment,
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
    create,
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
    file,
    flow,
    focus,
    for_,
    foreach,
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
    inlet,
    inlets,
    input,
    invariant,
    items,
    label,
    link,
    many,
    mapping,
    merge,
    message,
    module,
    morph,
    name,
    nebula,
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
    pipe,
    plant,
    projector,
    query,
    range,
    reference,
    relationship,
    remove,
    replica,
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
    void,
    when,
    where,
    with_
  )

}
