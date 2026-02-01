#!/usr/bin/env python3
"""
EBNF to TatSu Preprocessor

Converts RIDDL's EBNF grammar to TatSu-compatible format.
TatSu uses PEG (Parsing Expression Grammar) semantics but reads EBNF-like syntax.

Key conversions:
- Comments: (* comment *) -> # comment
- Character ranges: "A" | "B" | ... | "Z" -> /[A-Z]/
- Repetition: {x}+ stays as {x}+, {x} stays as {x}
- Optional: [x] stays as [x]
- TatSu doesn't support bare x+ outside braces

TatSu syntax reference: https://tatsu.readthedocs.io/en/stable/syntax.html
"""

import re
from typing import List, Tuple


def preprocess_for_tatsu(ebnf_content: str) -> str:
    """
    Convert RIDDL EBNF to TatSu-compatible format.

    Args:
        ebnf_content: The original EBNF content from ebnf-grammar.ebnf

    Returns:
        TatSu-compatible grammar string
    """
    result = ebnf_content

    # 1. Convert EBNF comments (* ... *) to TatSu comments # ...
    # Handle multi-line comments
    result = re.sub(r'\(\*([^*]*(?:\*(?!\))[^*]*)*)\*\)', _convert_comment, result)

    # 2. Convert ellipsis character ranges to regex patterns
    # "A" | "B" | ... | "Z" -> /[A-Z]/
    result = re.sub(
        r'"([A-Za-z])"\s*\|\s*"([A-Za-z])"\s*\|\s*\.\.\.\s*\|\s*"([A-Za-z])"',
        lambda m: f'/[{m.group(1)}-{m.group(3)}]/',
        result
    )

    # 3. Convert digit ranges if present
    # "0" | "1" | ... | "9" -> /[0-9]/
    result = re.sub(
        r'"(\d)"\s*\|\s*"(\d)"\s*\|\s*\.\.\.\s*\|\s*"(\d)"',
        lambda m: f'/[{m.group(1)}-{m.group(3)}]/',
        result
    )

    # 4. TatSu-specific: function call syntax like cardinality(...) is not valid
    # Convert cardinality(type_expression) to just the content with cardinality prefix
    # This is a complex grammar feature - for now, simplify by removing the function syntax
    result = re.sub(r'cardinality\s*\(\s*\n?', '(', result)

    # 5. TatSu header with start rule and whitespace handling
    header = """# RIDDL Grammar in TatSu Format
# AUTO-GENERATED from ebnf-grammar.ebnf by ebnf_preprocessor.py
# DO NOT EDIT MANUALLY

@@grammar :: RIDDL
@@whitespace :: /[\\s]+/

# Start rule - uses closure for one-or-more
start = {root_content}+ $ ;

"""

    # 6. Clean up any empty lines left over
    result = re.sub(r'\n{3,}', '\n\n', result)

    return header + result


def _convert_comment(match: re.Match) -> str:
    """Convert a single EBNF comment to TatSu format."""
    comment_text = match.group(1).strip()
    lines = comment_text.split('\n')
    return '\n'.join(f'# {line.strip()}' for line in lines)


def extract_rules(ebnf_content: str) -> List[Tuple[str, str]]:
    """
    Extract all rules from EBNF content as (name, body) tuples.

    Args:
        ebnf_content: EBNF grammar content

    Returns:
        List of (rule_name, rule_body) tuples
    """
    rules = []

    # Remove comments first
    content = re.sub(r'\(\*[^*]*(?:\*(?!\))[^*]*)*\*\)', '', ebnf_content)

    # Match rules: name = body ;
    # Handle multi-line rules
    current_rule = ""
    for line in content.split('\n'):
        line = line.strip()
        if not line:
            continue

        current_rule += " " + line

        if ';' in current_rule:
            # Could have multiple rules on one line
            for rule_match in re.finditer(r'(\w+)\s*=\s*(.+?)\s*;', current_rule):
                name = rule_match.group(1)
                body = rule_match.group(2).strip()
                rules.append((name, body))
            current_rule = ""

    return rules


def find_keywords(ebnf_content: str) -> set:
    """
    Find all keyword literals in the grammar.

    Returns set of keyword strings that appear as terminals.
    """
    keywords = set()

    # Find all quoted strings that look like keywords (lowercase identifiers)
    for match in re.finditer(r'"([a-z][a-z_]*)"', ebnf_content):
        keyword = match.group(1)
        # Skip single characters and special tokens
        if len(keyword) > 1:
            keywords.add(keyword)

    return keywords


if __name__ == "__main__":
    import argparse
    from pathlib import Path

    parser = argparse.ArgumentParser(description="Convert EBNF to TatSu format")
    parser.add_argument("input", type=Path, help="Input EBNF file")
    parser.add_argument("-o", "--output", type=Path, help="Output file")
    parser.add_argument("--show-keywords", action="store_true",
                        help="Show keywords found in grammar")

    args = parser.parse_args()

    with open(args.input, 'r', encoding='utf-8') as f:
        content = f.read()

    if args.show_keywords:
        keywords = find_keywords(content)
        print("Keywords found:")
        for kw in sorted(keywords):
            print(f"  {kw}")
    else:
        result = preprocess_for_tatsu(content)
        if args.output:
            with open(args.output, 'w', encoding='utf-8') as f:
                f.write(result)
            print(f"Wrote TatSu grammar to {args.output}")
        else:
            print(result)
