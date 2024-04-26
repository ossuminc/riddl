// Example .flex file (Lexer.flex)
%%
%class Lexer
%standalone
%unicode
%public
%type Token

%{

DIGIT = [0-9]
NUMBER=[]
    // Java code or imports here
%}

// Lexer rules
DIGIT = [0-9]
PLUS = "+"
TIMES = "*"
// ... other rules ...

%%
// Java code for actions (e.g., return new Token(...))

// Fastparse parser
import fastparse._
import NoWhitespace._

object MyParser {
  def number[_: P]: P[Int] = P(CharIn("0-9").rep(1).!.map(_.toInt))
  def plus[_: P]: P[Unit] = P("+")
  def times[_: P]: P[Unit] = P("*")

  def expr[_: P]: P[Int] = P(
    number ~ (plus ~ number | times ~ number).rep
  ).map { case (n, ops) =>
    ops.foldLeft(n) {
      case (acc, ("+", num)) => acc + num
      case (acc, ("*", num)) => acc * num
    }
  }

  def parse(input: String): Int = {
    fastparse.parse(input, expr(_)) match {
      case Parsed.Success(result, _) => result
      case Parsed.Failure(_, _, extra) =>
        throw new IllegalArgumentException(s"Failed to parse: $extra")
    }
  }
}
