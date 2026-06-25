#!/usr/bin/env python3
"""
EBNF-to-GBNF Converter for RIDDL

Converts RIDDL's EBNF grammar to GBNF format (llama.cpp
constrained generation). Handles whitespace insertion
automatically by classifying rules as terminal vs structural.

Usage:
    python ebnf_to_gbnf.py [--input PATH] [--overrides PATH]
                           [--output PATH] [--check] [--verbose]
                           [--show-classification]
"""

import re
import sys
import hashlib
import argparse
from pathlib import Path
from collections import OrderedDict
from datetime import datetime, timezone
from typing import Optional

# ── Paths ──

SCRIPT_DIR = Path(__file__).parent
LANGUAGE_DIR = SCRIPT_DIR.parent.parent.parent.parent
DEFAULT_EBNF = (
    LANGUAGE_DIR
    / "shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf"
)
DEFAULT_OUTPUT = DEFAULT_EBNF.parent / "riddl-grammar.gbnf"
DEFAULT_OVERRIDES = SCRIPT_DIR / "gbnf_overrides.gbnf"

# ── Terminal Classification Overrides ──

# Rules that should be forced terminal even if the heuristic
# might classify them as structural
FORCE_TERMINAL = {
    "path_identifier",
    "http_url",
    "host_string",
    "url_path",
    "port_num",
    "mime_type",
    "option_name",
    "markdown_line",
    "relationship_cardinality",
    "is",
    "byAs",
    "code_statement",
    "code_contents",
    "literal_string",
    "escape_sequence",
    "identifier",
    "quoted_identifier",
    "simple_identifier",
    "doc_block",
    "markdown_lines",
    "literal_strings",
    "zone",
    "enum_value",
    "end_of_line_comment",
    "comment",
    "inline_comment",
}

# Rules whose names end with alphabetic chars and are commonly
# followed by identifier-like rules (need ws1 not ws)
KEYWORD_TERMINALS = {
    "domain", "context", "entity", "adaptor", "projector",
    "repository", "saga", "epic", "module", "function",
    "handler", "state", "type", "command", "query", "event",
    "result", "record", "graph", "table", "source", "sink",
    "flow", "merge", "split", "router", "void", "constant",
    "invariant", "connector", "inlet", "outlet", "user",
    "author", "schema", "step", "case", "relationship",
    "include", "import",
}


# ── Data Structures ──

class EbnfRule:
    def __init__(self, name: str, body: str, section: str = ""):
        self.name = name
        self.body = body
        self.section = section
        self.is_terminal = False


class GbnfRule:
    def __init__(self, name: str, body: str,
                 comment: str = "",
                 from_override: bool = False):
        self.name = name
        self.body = body
        self.comment = comment
        self.from_override = from_override


# ── Stage 1: Parse EBNF ──

def parse_ebnf(content: str) -> OrderedDict:
    """Parse EBNF content into ordered dict of EbnfRules."""
    rules = OrderedDict()
    current_section = ""

    # Remove comments but track section headers
    lines = content.split("\n")
    clean_lines = []
    for line in lines:
        # Check for section comment
        m = re.match(r"^\(\*\s*(.+?)\s*\*\)\s*$", line)
        if m:
            current_section = m.group(1)
            continue
        # Remove inline comments
        line = re.sub(
            r"\(\*[^*]*(?:\*(?!\))[^*]*)*\*\)", "", line
        )
        clean_lines.append(line)

    # Join and split on semicolons to get rules
    full_text = " ".join(clean_lines)

    # Match rules: name = body ;
    # Use a state machine to handle nested { } [ ] ( ) / /
    pos = 0
    section_for_next = current_section
    current_section = ""

    for m in re.finditer(
        r"(\w+)\s*=\s*", full_text
    ):
        name = m.group(1)
        start = m.end()
        # Find the terminating ; accounting for nesting
        depth_brace = 0
        depth_bracket = 0
        depth_paren = 0
        in_string = False
        in_regex = False
        i = start
        while i < len(full_text):
            c = full_text[i]
            if in_string:
                if c == "\\" and i + 1 < len(full_text):
                    i += 2
                    continue
                if c == '"':
                    in_string = False
            elif in_regex:
                if c == "\\" and i + 1 < len(full_text):
                    i += 2
                    continue
                if c == "/":
                    in_regex = False
            else:
                if c == '"':
                    in_string = True
                elif c == "/" and (
                    i == start
                    or full_text[i - 1] in " \t\n|("
                ):
                    in_regex = True
                elif c == "{":
                    depth_brace += 1
                elif c == "}":
                    depth_brace -= 1
                elif c == "[":
                    depth_bracket += 1
                elif c == "]":
                    depth_bracket -= 1
                elif c == "(":
                    depth_paren += 1
                elif c == ")":
                    depth_paren -= 1
                elif (
                    c == ";"
                    and depth_brace == 0
                    and depth_bracket == 0
                    and depth_paren == 0
                ):
                    body = full_text[start:i].strip()
                    # Determine section from comment
                    # before this rule
                    sec = _find_section(
                        content, name
                    )
                    rules[name] = EbnfRule(name, body, sec)
                    break
            i += 1

    return rules


def _find_section(content: str, rule_name: str) -> str:
    """Find the section comment preceding a rule."""
    lines = content.split("\n")
    section = ""
    for line in lines:
        m = re.match(r"^\(\*\s*(.+?)\s*\*\)\s*$", line)
        if m:
            section = m.group(1)
        if re.match(rf"^{rule_name}\s*=", line):
            return section
    return ""


# ── Stage 2: Classify Rules ──

def is_pure_regex(body: str) -> bool:
    """Check if rule body is a single regex pattern."""
    stripped = body.strip()
    return (
        stripped.startswith("/")
        and stripped.endswith("/")
        and stripped.count("/") == 2
    )


def extract_rule_refs(body: str) -> set:
    """Extract rule name references from a body."""
    refs = set()
    # Tokenize: skip strings and regexes
    i = 0
    while i < len(body):
        c = body[i]
        if c == '"':
            # Skip string
            i += 1
            while i < len(body) and body[i] != '"':
                if body[i] == "\\":
                    i += 1
                i += 1
            i += 1
        elif c == "/" and (
            i == 0 or body[i - 1] in " \t\n|({["
        ):
            # Skip regex
            i += 1
            while i < len(body) and body[i] != "/":
                if body[i] == "\\":
                    i += 1
                i += 1
            i += 1
        elif re.match(r"[A-Za-z_]", c):
            # Rule reference
            m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", body[i:])
            if m:
                refs.add(m.group(0))
                i += len(m.group(0))
            else:
                i += 1
        else:
            i += 1
    return refs


def classify_rules(rules: OrderedDict, verbose: bool = False):
    """Classify rules as terminal or structural. Mutates."""
    terminal = set()

    # Phase 1: seed with pure regex
    for name, rule in rules.items():
        if is_pure_regex(rule.body):
            terminal.add(name)

    # Phase 2: propagate
    changed = True
    while changed:
        changed = False
        for name, rule in rules.items():
            if name in terminal:
                continue
            refs = extract_rule_refs(rule.body)
            # Remove self-refs and non-rule keywords
            rule_refs = refs & set(rules.keys())
            non_terminal_refs = rule_refs - terminal
            if len(non_terminal_refs) == 0 and len(
                rule_refs
            ) > 0:
                terminal.add(name)
                changed = True

    # Phase 3: force overrides
    terminal |= FORCE_TERMINAL & set(rules.keys())

    # Apply
    for name, rule in rules.items():
        rule.is_terminal = name in terminal

    if verbose:
        print(f"Terminal rules ({len(terminal)}):")
        for n in sorted(terminal):
            if n in rules:
                print(f"  {n}")
        structural = set(rules.keys()) - terminal
        print(f"Structural rules ({len(structural)}):")
        for n in sorted(structural):
            print(f"  {n}")


# ── Stage 3: Convert Rule Bodies ──

def to_kebab(name: str) -> str:
    """Convert snake_case or camelCase to kebab-case."""
    # First handle camelCase: insert - before uppercase
    result = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name)
    # Then handle snake_case
    result = result.replace("_", "-")
    return result.lower()


def regex_to_gbnf(pattern: str) -> str:
    """Convert a regex pattern to GBNF expression."""
    # Check for negative lookahead
    if "(?!" in pattern:
        raise ValueError(
            f"Negative lookahead in regex: {pattern} "
            "— use gbnf_overrides.gbnf"
        )

    result = []
    i = 0
    while i < len(pattern):
        c = pattern[i]

        if c == "[":
            # Character class — pass through to end
            j = i + 1
            if j < len(pattern) and pattern[j] == "^":
                j += 1
            if j < len(pattern) and pattern[j] == "]":
                j += 1
            while j < len(pattern) and pattern[j] != "]":
                if pattern[j] == "\\":
                    j += 1
                j += 1
            j += 1  # past ]
            char_class = pattern[i:j]
            # Strip GBNF-invalid escapes from the char class: regex escapes
            # like \/ are literals in GBNF (llama.cpp rejects unknown escapes).
            # Keep \\ \] and control/hex/unicode escapes (n r t x u \).
            _cc, _k = [], 0
            while _k < len(char_class):
                if char_class[_k] == "\\" and _k + 1 < len(char_class):
                    _nx = char_class[_k + 1]
                    if _nx in "nrtxu]\\":
                        _cc.append(char_class[_k])
                        _cc.append(_nx)
                    else:
                        _cc.append(_nx)
                    _k += 2
                else:
                    _cc.append(char_class[_k])
                    _k += 1
            char_class = "".join(_cc)
            # Check for trailing quantifier and attach it
            quant = ""
            if j < len(pattern) and pattern[j] in "+*?":
                quant = pattern[j]
                j += 1
            elif j < len(pattern) and pattern[j] == "{":
                # Expand {m,n} quantifier
                k = pattern.index("}", j)
                quant_spec = pattern[j+1:k]
                expanded = _expand_quantifier(
                    char_class, quant_spec
                )
                result.append(expanded)
                i = k + 1
                continue
            result.append(f"{char_class}{quant}")
            i = j

        elif c == "\\":
            # Escape sequence
            if i + 1 < len(pattern):
                nc = pattern[i + 1]
                if nc in "nrt":
                    result.append(f'"\\{nc}"')
                    i += 2
                elif nc in "/":
                    result.append(f'"/"')
                    i += 2
                elif nc in "\\":
                    result.append('"\\\\"')
                    i += 2
                elif nc == '"':
                    result.append('"\\""')
                    i += 2
                elif nc == "x":
                    # Hex escape: \xNN...
                    j = i + 2
                    while (
                        j < len(pattern)
                        and j < i + 10
                        and pattern[j] in "0123456789abcdefABCDEF"
                    ):
                        j += 1
                    result.append(f'"{pattern[i:j]}"')
                    i = j
                elif nc == "u":
                    result.append(f'"{pattern[i:i+6]}"')
                    i += 6
                else:
                    # Other escaped char (literal): drop the backslash — the
                    # bare char is a literal in a GBNF double-quoted string
                    # (e.g. regex \. -> ".", \* -> "*"). GBNF rejects "\.".
                    result.append(f'"{nc}"')
                    i += 2
            else:
                result.append('"\\\\"')
                i += 1

        elif c == "(":
            # Group — check for non-capturing (?:
            if pattern[i:i+3] == "(?:":
                result.append("(")
                i += 3
            elif pattern[i:i+2] == "(?":
                raise ValueError(
                    f"Unsupported group at pos {i}: "
                    f"{pattern[i:i+10]}"
                )
            else:
                result.append("(")
                i += 1

        elif c == ")":
            # Check for trailing quantifier
            quant = ""
            if (
                i + 1 < len(pattern)
                and pattern[i + 1] in "+*?"
            ):
                quant = pattern[i + 1]
                i += 2
            else:
                i += 1
            result.append(f"){quant}")

        elif c == "|":
            result.append(" | ")
            i += 1

        elif c in "+*?":
            # Standalone quantifier on previous atom
            result[-1] = result[-1] + c
            i += 1

        elif c == ".":
            result.append(".")
            i += 1

        elif c in " \t":
            i += 1

        else:
            # Literal character — accumulate
            lit = ""
            while (
                i < len(pattern)
                and pattern[i] not in r"[]\()|+*?{}./"
                and pattern[i] != "\\"
                and pattern[i] not in " \t"
            ):
                lit += pattern[i]
                i += 1
            if lit:
                result.append(f'"{lit}"')

    # Post-process: merge adjacent string literals
    merged = []
    for item in result:
        if (
            merged
            and merged[-1].startswith('"')
            and merged[-1].endswith('"')
            and item.startswith('"')
            and item.endswith('"')
            and " " not in item[1:-1]
            and "|" not in item
        ):
            # Merge: "a" + "b" -> "ab"
            merged[-1] = merged[-1][:-1] + item[1:]
        else:
            merged.append(item)

    return " ".join(merged)


def _expand_quantifier(element: str, spec: str) -> str:
    """Expand a {m,n} or {m} quantifier."""
    if "," in spec:
        parts = spec.split(",")
        m_val = int(parts[0])
        n_str = parts[1].strip()
        if n_str:
            n_val = int(n_str)
            return " ".join(
                [element] * m_val
                + [f"{element}?"] * (n_val - m_val)
            )
        else:
            # {m,} = m required + *
            return " ".join(
                [element] * m_val
            ) + f" {element}*"
    else:
        m_val = int(spec)
        return " ".join([element] * m_val)


def convert_body(body: str, rules: OrderedDict) -> str:
    """Convert an EBNF rule body to GBNF syntax."""
    tokens = tokenize_ebnf(body)
    gbnf_tokens = []

    i = 0
    while i < len(tokens):
        tok = tokens[i]
        typ, val = tok

        if typ == "STRING":
            gbnf_tokens.append(val)
            i += 1

        elif typ == "REGEX":
            # Strip delimiters
            pattern = val[1:-1]
            try:
                gbnf_tokens.append(regex_to_gbnf(pattern))
            except ValueError as e:
                # Lookahead — will be replaced by override
                gbnf_tokens.append(
                    f"/* OVERRIDE NEEDED: {e} */"
                )
            i += 1

        elif typ == "RULE_REF":
            gbnf_tokens.append(to_kebab(val))
            i += 1

        elif typ == "LBRACE":
            # { ... } or { ... }+
            # Find matching }
            depth = 1
            j = i + 1
            while j < len(tokens) and depth > 0:
                if tokens[j][0] == "LBRACE":
                    depth += 1
                elif tokens[j][0] == "RBRACE":
                    depth -= 1
                j += 1
            # Extract inner tokens
            inner = tokens[i+1:j-1]
            inner_str = convert_body_from_tokens(
                inner, rules
            )
            # Check for trailing +
            if (
                j < len(tokens)
                and tokens[j][0] == "PLUS"
            ):
                gbnf_tokens.append(f"({inner_str})+")
                i = j + 1
            else:
                gbnf_tokens.append(f"({inner_str})*")
                i = j

        elif typ == "LBRACKET":
            # [ ... ] optional
            depth = 1
            j = i + 1
            while j < len(tokens) and depth > 0:
                if tokens[j][0] == "LBRACKET":
                    depth += 1
                elif tokens[j][0] == "RBRACKET":
                    depth -= 1
                j += 1
            inner = tokens[i+1:j-1]
            inner_str = convert_body_from_tokens(
                inner, rules
            )
            gbnf_tokens.append(f"({inner_str})?")
            i = j

        elif typ == "LPAREN":
            depth = 1
            j = i + 1
            while j < len(tokens) and depth > 0:
                if tokens[j][0] == "LPAREN":
                    depth += 1
                elif tokens[j][0] == "RPAREN":
                    depth -= 1
                j += 1
            inner = tokens[i+1:j-1]
            inner_str = convert_body_from_tokens(
                inner, rules
            )
            # Check for trailing quantifier
            quant = ""
            if j < len(tokens) and tokens[j][0] in (
                "PLUS", "STAR", "QUESTION"
            ):
                quant = tokens[j][1]
                j += 1
            gbnf_tokens.append(f"({inner_str}){quant}")
            i = j

        elif typ == "PIPE":
            gbnf_tokens.append("|")
            i += 1

        elif typ in ("PLUS", "STAR", "QUESTION"):
            gbnf_tokens.append(val)
            i += 1

        elif typ == "COMMA":
            # Commas in EBNF are separators, skip
            i += 1

        else:
            i += 1

    return " ".join(gbnf_tokens)


def convert_body_from_tokens(tokens, rules):
    """Convert already-tokenized EBNF to GBNF string."""
    # Reconstruct a body string and re-convert
    # This is a helper for nested { } and [ ] blocks
    parts = []
    for typ, val in tokens:
        parts.append(val)
    body = " ".join(parts)
    return convert_body(body, rules)


def tokenize_ebnf(body: str) -> list:
    """Tokenize an EBNF body into (type, value) pairs."""
    tokens = []
    i = 0
    while i < len(body):
        c = body[i]

        # Whitespace
        if c in " \t\n\r":
            i += 1
            continue

        # String literal
        if c == '"':
            j = i + 1
            while j < len(body):
                if body[j] == "\\" and j + 1 < len(body):
                    j += 2
                    continue
                if body[j] == '"':
                    break
                j += 1
            tokens.append(("STRING", body[i:j+1]))
            i = j + 1

        # Single-quoted EBNF literal -> GBNF double-quoted literal.
        elif c == "'":
            j = i + 1
            while j < len(body) and body[j] != "'":
                j += 1
            content = body[i + 1:j]
            # GBNF has no single-quoted literals; escape " and \ for a proper
            # double-quoted GBNF literal (e.g. '"' -> "\"").
            escaped = content.replace("\\", "\\\\").replace('"', '\\"')
            tokens.append(("STRING", '"' + escaped + '"'))
            i = j + 1

        # Regex
        elif c == "/" and (
            i == 0
            or body[i-1] in " \t\n|({["
            or tokens[-1][0] in (
                "PIPE", "LPAREN", "LBRACE", "LBRACKET"
            )
            if tokens else True
        ):
            j = i + 1
            while j < len(body):
                if body[j] == "\\" and j + 1 < len(body):
                    j += 2
                    continue
                if body[j] == "/":
                    break
                j += 1
            tokens.append(("REGEX", body[i:j+1]))
            i = j + 1

        elif c == "{":
            tokens.append(("LBRACE", "{"))
            i += 1
        elif c == "}":
            tokens.append(("RBRACE", "}"))
            i += 1
        elif c == "[":
            tokens.append(("LBRACKET", "["))
            i += 1
        elif c == "]":
            tokens.append(("RBRACKET", "]"))
            i += 1
        elif c == "(":
            tokens.append(("LPAREN", "("))
            i += 1
        elif c == ")":
            tokens.append(("RPAREN", ")"))
            i += 1
        elif c == "|":
            tokens.append(("PIPE", "|"))
            i += 1
        elif c == "+":
            tokens.append(("PLUS", "+"))
            i += 1
        elif c == "*":
            tokens.append(("STAR", "*"))
            i += 1
        elif c == "?":
            tokens.append(("QUESTION", "?"))
            i += 1
        elif c == ",":
            tokens.append(("COMMA", ","))
            i += 1
        elif c == ";":
            break  # End of rule

        # Rule reference or keyword
        elif re.match(r"[A-Za-z_]", c):
            m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", body[i:])
            if m:
                tokens.append(("RULE_REF", m.group(0)))
                i += len(m.group(0))
            else:
                i += 1
        else:
            i += 1

    return tokens


# ── Stage 4: Insert Whitespace ──

def needs_ws1(prev_token: str, next_token: str) -> bool:
    """Check if ws1 (required space) needed between tokens."""
    # After alphabetic keyword, before rule ref
    prev_stripped = prev_token.strip('"')
    if (
        prev_stripped
        and prev_stripped[-1].isalpha()
        and not prev_token.startswith("(")
        and not prev_token.startswith("[")
    ):
        # Next is a rule ref (kebab-case, no quotes)
        if (
            next_token
            and next_token[0].isalpha()
            and not next_token.startswith('"')
        ):
            return True
    return False


def insert_whitespace(gbnf_body: str, is_terminal: bool) -> str:
    """Insert ws/ws1 between tokens for structural rules."""
    if is_terminal:
        return gbnf_body

    # Split into tokens preserving grouping
    parts = _split_gbnf_tokens(gbnf_body)
    if len(parts) <= 1:
        return gbnf_body

    result = [parts[0]]
    for i in range(1, len(parts)):
        prev = parts[i - 1]
        curr = parts[i]

        # Skip ws insertion around | (alternatives)
        if curr == "|" or prev == "|":
            result.append(curr)
            continue

        # Skip ws before quantifiers
        if curr in ("+", "*", "?"):
            result.append(curr)
            continue

        # Skip if prev is a quantifier
        if prev in ("+", "*", "?"):
            result.append("ws")
            result.append(curr)
            continue

        # Both are content tokens — insert ws
        if _is_content(prev) and _is_content(curr):
            if needs_ws1(prev, curr):
                result.append("ws1")
            else:
                result.append("ws")
            result.append(curr)
        else:
            result.append(curr)

    return " ".join(result)


def _split_gbnf_tokens(body: str) -> list:
    """Split GBNF body into tokens for ws insertion."""
    tokens = []
    i = 0
    while i < len(body):
        c = body[i]

        if c in " \t\n\r":
            i += 1
            continue

        # String literal
        if c == '"':
            j = i + 1
            while j < len(body):
                if body[j] == "\\" and j + 1 < len(body):
                    j += 2
                    continue
                if body[j] == '"':
                    break
                j += 1
            tokens.append(body[i:j+1])
            i = j + 1

        # Grouped expression with parens
        elif c == "(":
            depth = 1
            j = i + 1
            while j < len(body) and depth > 0:
                if body[j] == '"':
                    j += 1
                    while j < len(body) and body[j] != '"':
                        if body[j] == "\\":
                            j += 1
                        j += 1
                    j += 1
                    continue
                if body[j] == "(":
                    depth += 1
                elif body[j] == ")":
                    depth -= 1
                j += 1
            # Include trailing quantifier
            if j < len(body) and body[j] in "+*?":
                j += 1
            tokens.append(body[i:j])
            i = j

        # Character class
        elif c == "[":
            j = i + 1
            if j < len(body) and body[j] == "^":
                j += 1
            while j < len(body) and body[j] != "]":
                if body[j] == "\\":
                    j += 1
                j += 1
            j += 1
            # Include trailing quantifier
            if j < len(body) and body[j] in "+*?":
                j += 1
            tokens.append(body[i:j])
            i = j

        elif c == "|":
            tokens.append("|")
            i += 1

        elif c in "+*?":
            tokens.append(c)
            i += 1

        # Rule reference (kebab-case)
        elif re.match(r"[a-z]", c):
            m = re.match(r"[a-z][a-z0-9-]*", body[i:])
            if m:
                tokens.append(m.group(0))
                i += len(m.group(0))
            else:
                i += 1

        else:
            i += 1

    return tokens


def _is_content(token: str) -> bool:
    """Check if token is a content token (not operator)."""
    if token in ("|", "+", "*", "?"):
        return False
    return bool(token.strip())


# ── Stage 5: Merge Overrides ──

def load_overrides(path: Path) -> dict:
    """Load override rules from a GBNF file."""
    overrides = {}
    if not path.exists():
        return overrides

    content = path.read_text(encoding="utf-8")
    for m in re.finditer(
        r"^([a-z][a-z0-9-]*)\s*::=\s*(.+)$",
        content,
        re.MULTILINE,
    ):
        name = m.group(1)
        body = m.group(2).strip()
        overrides[name] = body

    return overrides


def compute_fingerprint(body: str) -> str:
    """Compute a short hash of a rule body for staleness."""
    return hashlib.md5(
        body.encode("utf-8")
    ).hexdigest()[:8]


# Known EBNF fingerprints for override rules
# Updated when overrides are verified correct
OVERRIDE_FINGERPRINTS = {
    "any_char_except_end_comment":
        None,  # Will be set on first run
    "any_char_except_triple_backtick":
        None,
    "inline_comment":
        None,
}


def check_override_staleness(
    rules: OrderedDict,
    overrides: dict,
    verbose: bool = False,
):
    """Warn if overridden EBNF rules have changed."""
    for ebnf_name, expected_fp in OVERRIDE_FINGERPRINTS.items():
        gbnf_name = to_kebab(ebnf_name)
        if gbnf_name not in overrides:
            continue
        if ebnf_name not in rules:
            print(
                f"WARNING: Override '{gbnf_name}' has no "
                f"matching EBNF rule '{ebnf_name}'",
                file=sys.stderr,
            )
            continue
        if expected_fp is not None:
            actual_fp = compute_fingerprint(
                rules[ebnf_name].body
            )
            if actual_fp != expected_fp:
                print(
                    f"WARNING: EBNF rule '{ebnf_name}' has "
                    f"changed (was {expected_fp}, now "
                    f"{actual_fp}). Override "
                    f"'{gbnf_name}' may be stale.",
                    file=sys.stderr,
                )
        elif verbose:
            actual_fp = compute_fingerprint(
                rules[ebnf_name].body
            )
            print(
                f"  Override '{gbnf_name}': EBNF fingerprint"
                f" = {actual_fp}",
                file=sys.stderr,
            )


# ── Stage 6: Emit GBNF ──

def emit_gbnf(
    rules: OrderedDict,
    gbnf_rules: OrderedDict,
    output: Path,
):
    """Write the final GBNF file."""
    now = datetime.now(timezone.utc).strftime(
        "%Y-%m-%d %H:%M UTC"
    )
    lines = [
        "# RIDDL Grammar in GBNF format",
        "# (llama.cpp constrained generation)",
        "# AUTO-GENERATED from ebnf-grammar.ebnf by "
        "ebnf_to_gbnf.py",
        "# DO NOT EDIT - modify the source EBNF or "
        "gbnf_overrides.gbnf instead",
        f"# Generated: {now}",
        "",
        "# Whitespace primitives",
        'ws ::= [ \\t\\n\\r]*',
        'ws1 ::= [ \\t\\n\\r]+',
        "",
    ]

    current_section = ""
    for name, grule in gbnf_rules.items():
        # Section header
        ebnf_rule = rules.get(
            name.replace("-", "_")
        )
        section = ebnf_rule.section if ebnf_rule else ""
        if section and section != current_section:
            current_section = section
            lines.append("")
            lines.append(f"# {section}")

        # Rule
        comment = ""
        if grule.from_override:
            comment = "  # (from override)"
        lines.append(
            f"{grule.name} ::= {grule.body}{comment}"
        )

    content = "\n".join(lines) + "\n"
    output.write_text(content, encoding="utf-8")


# ── Main Pipeline ──

def convert(
    ebnf_path: Path,
    overrides_path: Path,
    output_path: Path,
    verbose: bool = False,
    show_classification: bool = False,
    check: bool = False,
):
    """Run the full conversion pipeline."""
    # Stage 1: Parse
    content = ebnf_path.read_text(encoding="utf-8")
    rules = parse_ebnf(content)
    if verbose:
        print(f"Parsed {len(rules)} EBNF rules")

    # Stage 2: Classify
    classify_rules(rules, verbose=show_classification)
    if show_classification:
        return

    # Stage 3 + 4: Convert and insert whitespace
    gbnf_rules = OrderedDict()

    # Add root rule first (GBNF requires it)
    if "root" in rules:
        root_body = convert_body(rules["root"].body, rules)
        root_body = insert_whitespace(root_body, False)
        gbnf_rules["root"] = GbnfRule("root", root_body)

    for name, rule in rules.items():
        if name == "root":
            continue
        gbnf_name = to_kebab(name)
        gbnf_body = convert_body(rule.body, rules)
        gbnf_body = insert_whitespace(
            gbnf_body, rule.is_terminal
        )
        gbnf_rules[gbnf_name] = GbnfRule(
            gbnf_name, gbnf_body
        )

    if verbose:
        print(f"Converted {len(gbnf_rules)} rules")

    # Stage 5: Merge overrides
    overrides = load_overrides(overrides_path)
    if verbose:
        print(f"Loaded {len(overrides)} overrides")

    check_override_staleness(rules, overrides, verbose)

    for oname, obody in overrides.items():
        if oname in gbnf_rules:
            gbnf_rules[oname] = GbnfRule(
                oname, obody, from_override=True
            )
        else:
            print(
                f"WARNING: Override '{oname}' not found "
                f"in EBNF rules",
                file=sys.stderr,
            )

    # Stage 6: Emit
    if check:
        # Generate to temp and compare
        import tempfile
        with tempfile.NamedTemporaryFile(
            mode="w",
            suffix=".gbnf",
            delete=False,
        ) as f:
            tmp_path = Path(f.name)
        emit_gbnf(rules, gbnf_rules, tmp_path)
        if output_path.exists():
            existing = output_path.read_text(encoding="utf-8")
            generated = tmp_path.read_text(encoding="utf-8")
            # Strip timestamps for comparison
            existing_stripped = re.sub(
                r"# Generated:.*\n", "", existing
            )
            generated_stripped = re.sub(
                r"# Generated:.*\n", "", generated
            )
            if existing_stripped != generated_stripped:
                print(
                    "ERROR: Generated GBNF differs from "
                    "committed version. Run ebnf_to_gbnf.py "
                    "and commit the result.",
                    file=sys.stderr,
                )
                tmp_path.unlink()
                sys.exit(1)
            else:
                print("GBNF is up-to-date")
                tmp_path.unlink()
        else:
            print(
                f"ERROR: {output_path} does not exist",
                file=sys.stderr,
            )
            tmp_path.unlink()
            sys.exit(1)
    else:
        emit_gbnf(rules, gbnf_rules, output_path)
        print(
            f"Generated {output_path} "
            f"({len(gbnf_rules)} rules)"
        )


def main():
    parser = argparse.ArgumentParser(
        description="Convert RIDDL EBNF grammar to GBNF format"
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_EBNF,
        help="Input EBNF file",
    )
    parser.add_argument(
        "--overrides",
        type=Path,
        default=DEFAULT_OVERRIDES,
        help="Overrides GBNF file",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Output GBNF file",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Check mode: exit 1 if output differs",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Show conversion details",
    )
    parser.add_argument(
        "--show-classification",
        action="store_true",
        help="Print terminal/structural classification",
    )

    args = parser.parse_args()
    convert(
        args.input,
        args.overrides,
        args.output,
        verbose=args.verbose,
        show_classification=args.show_classification,
        check=args.check,
    )


if __name__ == "__main__":
    main()
