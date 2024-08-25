package com.ossuminc.riddl.language.parsing

import AST.*

/** This Object contains the implementation of the Binary Abstract Syntax Tree for RIDDL */
object BAST {

  enum Element:
    Root(0),
  def compress(node: VitalDefinition[?]):
}

case class SimpleContainer[+CV <: ContentValues](contents: Contents[CV]) extends Container[CV]:

case class LiteralString(loc: At, s: String) extends RiddlValue:

case class Identifier(loc: At, value: String) extends RiddlValue:

case class PathIdentifier(loc: At, value: Seq[String]) extends RiddlValue:

case class BriefDescription(

case class BlockDescription(

case class URLDescription(loc: At, url: URL) extends Description:

case class LineComment(loc: At, text: String = "") extends Comment:

case class InlineComment(loc: At, lines: Seq[String] = Seq.empty) extends Comment:

case class OptionValue(loc: At, name: String, args: Seq[LiteralString] = Seq.empty) extends RiddlValue:

case class Include[CT <: RiddlValue](

case class Root(

case class User(

case class Term(

case class Author(

case class AuthorRef(loc: At, pathId: PathIdentifier) extends Reference[Author] {
  case class AliasedTypeExpression(loc: At, keyword: String, pathId: PathIdentifier) extends TypeExpression {
    case class Optional(loc: At, typeExp: TypeExpression) extends Cardinality {
      case class ZeroOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
        case class OneOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
          case class SpecificRange(

          case class Enumerator(

          case class Enumeration(loc: At, enumerators: Seq[Enumerator]) extends IntegerTypeExpression {
            case class Alternation(loc: At, of: Seq[AliasedTypeExpression]) extends TypeExpression {
              case class Sequence(loc: At, of: TypeExpression) extends TypeExpression {
                case class Mapping(loc: At, from: TypeExpression, to: TypeExpression) extends TypeExpression {
                  case class Set(loc: At, of: TypeExpression) extends TypeExpression {
                    case class Graph(loc: At, of: TypeExpression) extends TypeExpression {
                      case class Table(loc: At, of: TypeExpression, dimensions: Seq[Long]) extends TypeExpression {
                        case class Replica(loc: At, of: TypeExpression) extends TypeExpression {
                          case class Field(

                          case class MethodArgument(

                          case class Method(

                          case class Aggregation(

                          case class AggregateUseCaseTypeExpression(

                          case class EntityReferenceTypeExpression(loc: At, entity: PathIdentifier) extends TypeExpression {
                            case class Pattern(loc: At, pattern: Seq[LiteralString]) extends PredefinedType {
                              case class String_(loc: At, min: Option[Long] = None, max: Option[Long] = None) extends PredefinedType {
                                case class UniqueId(loc: At, entityPath: PathIdentifier) extends PredefinedType {
                                  case class Currency(loc: At, country: String) extends PredefinedType

                                  case class Abstract(loc: At) extends PredefinedType {
                                    case class UserId(loc: At) extends PredefinedType {
                                      case class Bool(loc: At) extends PredefinedType with IntegerTypeExpression {
                                        case class Number(loc: At) extends PredefinedType with IntegerTypeExpression with RealTypeExpression {}

                                        case class Integer(loc: At) extends PredefinedType with IntegerTypeExpression

                                        case class Whole(loc: At) extends PredefinedType with IntegerTypeExpression

                                        case class Natural(loc: At) extends PredefinedType with IntegerTypeExpression

                                        case class RangeType(loc: At, min: Long, max: Long) extends IntegerTypeExpression {
                                          case class Decimal(loc: At, whole: Long, fractional: Long) extends RealTypeExpression {
                                            case class Real(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Current(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Length(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Luminosity(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Mass(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Mole(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Temperature(loc: At) extends PredefinedType with RealTypeExpression

                                            case class Date(loc: At) extends TimeType {
                                              case class Time(loc: At) extends TimeType {
                                                case class DateTime(loc: At) extends TimeType {
                                                  case class ZonedDateTime(loc: At, zone: Option[LiteralString] = None) extends TimeType {
                                                    case class TimeStamp(loc: At) extends TimeType {
                                                      case class Duration(loc: At) extends TimeType

                                                      case class UUID(loc: At) extends PredefinedType

                                                      case class URI(loc: At, scheme: Option[LiteralString] = None) extends PredefinedType {
                                                        case class Location(loc: At) extends PredefinedType

                                                        case class Blob(loc: At, blobKind: BlobKind) extends PredefinedType {
                                                          case class Nothing(loc: At) extends PredefinedType {
                                                            case class CommandRef(

                                                            case class EventRef(

                                                            case class QueryRef(

                                                            case class ResultRef(

                                                            case class RecordRef(

                                                            case class Type(

                                                            case class TypeRef(

                                                            case class FieldRef(

                                                            case class Constant(

                                                            case class ConstantRef(

                                                            case class ArbitraryStatement(

                                                            case class ErrorStatement(

                                                            case class FocusStatement(

                                                            case class SetStatement(

                                                            case class ReturnStatement(

                                                            case class SendStatement(

                                                            case class ReplyStatement(

                                                            case class MorphStatement(

                                                            case class BecomeStatement(

                                                            case class TellStatement(

                                                            case class CallStatement(

                                                            case class ForEachStatement(

                                                            case class IfThenElseStatement(

                                                            case class StopStatement(

                                                            case class ReadStatement(

                                                            case class WriteStatement(

                                                            case class CodeStatement(

                                                            case class InboundAdaptor(loc: At) extends AdaptorDirection {
                                                              case class OutboundAdaptor(loc: At) extends AdaptorDirection {
                                                                case class Adaptor(

                                                                case class AdaptorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Adaptor] {
                                                                  case class Function(

                                                                  case class FunctionRef(loc: At, pathId: PathIdentifier) extends Reference[Function] {
                                                                    case class Invariant(

                                                                    case class OnOtherClause(

                                                                    case class OnInitializationClause(

                                                                    case class OnMessageClause(

                                                                    case class OnTerminationClause(

                                                                    case class Handler(

                                                                    case class HandlerRef(loc: At, pathId: PathIdentifier) extends Reference[Handler] {
                                                                      case class State(

                                                                      case class StateRef(loc: At, pathId: PathIdentifier) extends Reference[State]:

                                                                      case class Entity(

                                                                      case class EntityRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Entity]:

                                                                      case class Schema(

                                                                      case class Repository(

                                                                      case class RepositoryRef(loc: At, pathId: PathIdentifier) extends Reference[Repository] with ProcessorRef[Projector] {
                                                                        case class Projector(

                                                                        case class ProjectorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Projector] {
                                                                          case class Context(

                                                                          case class ContextRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Context] {
                                                                            case class Inlet(

                                                                            case class Outlet(

                                                                            case class Connector(

                                                                            case class Void(loc: At) extends StreamletShape {
                                                                              case class Source(loc: At) extends StreamletShape {
                                                                                case class Sink(loc: At) extends StreamletShape {
                                                                                  case class Flow(loc: At) extends StreamletShape {
                                                                                    case class Merge(loc: At) extends StreamletShape {
                                                                                      case class Split(loc: At) extends StreamletShape {
                                                                                        case class Router(loc: At) extends StreamletShape {
                                                                                          case class Streamlet(

                                                                                          case class StreamletRef(loc: At, keyword: String, pathId: PathIdentifier) extends ProcessorRef[Streamlet] {
                                                                                            case class InletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Inlet] {
                                                                                              case class OutletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Outlet] {
                                                                                                case class SagaStep(

                                                                                                case class Saga(

                                                                                                case class SagaRef(loc: At, pathId: PathIdentifier) extends Reference[Saga] {
                                                                                                  case class UserRef(loc: At, pathId: PathIdentifier) extends Reference[User] {

/** One abstract step in an Interaction between things. The set of case classes associated with this sealed trait
 * case class ParallelInteractions(
 * case class SequentialInteractions(
 * case class OptionalInteractions(
 * case class VagueInteraction(
 * case class SendMessageInteraction(
 * case class ArbitraryInteraction(
 * case class SelfInteraction(
 * case class FocusOnGroupInteraction(
 * case class DirectUserToURLInteraction(
 * case class ShowOutputInteraction(
 * case class SelectInputInteraction(
 * case class TakeInputInteraction(
 * case class UseCase(
 * case class UserStory(
 * case class ShownBy(
 * case class Epic(
 * case class EpicRef(loc: At, pathId: PathIdentifier) extends Reference[Epic] {
 * case class Group(
 * case class GroupRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Group] {
 * case class ContainedGroup(
 * case class Output(
 * case class OutputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Output] {
 * case class Input(
 * case class InputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Input] {
 * case class Application(
 * case class ApplicationRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Application] {
 * case class Domain(
 * case class DomainRef(lo
