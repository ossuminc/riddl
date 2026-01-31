#!/usr/bin/env python3
"""
RIDDL EBNF Grammar Validator

Validates RIDDL files against the EBNF-derived Lark grammar.
This ensures the documented EBNF grammar matches what FastParse accepts.

Usage:
    python ebnf_validator.py [--verbose] [--file <path>]

Options:
    --verbose    Show detailed parse trees for failures
    --file PATH  Test a single file instead of all test files
"""

import argparse
import sys
from pathlib import Path
from typing import List, Tuple, Optional

try:
    from lark import Lark, exceptions as lark_exceptions
except ImportError:
    print("Error: Lark parser not installed. Install with: pip install lark")
    sys.exit(1)

# Paths relative to this script
SCRIPT_DIR = Path(__file__).parent
GRAMMAR_FILE = SCRIPT_DIR / "riddl_grammar.lark"
# Go up from python/ -> test/ -> src/ -> jvm/ -> language/ to find input/
TEST_DIR = SCRIPT_DIR.parent.parent.parent.parent / "input"

# Files that are include fragments, not standalone RIDDL files.
# These are meant to be included within a parent container and cannot be parsed standalone.
INCLUDE_FRAGMENTS = {
    "everything_APlant.riddl",      # Context fragment included by everything.riddl
    "everything_full.riddl",         # Context fragment included by everything.riddl
    "everything_app.riddl",          # Context fragment included by everything.riddl
    "contextIncluded.riddl",         # Type definitions for context include
    "domainIncluded.riddl",          # Type definitions for domain include
    "someTypes.riddl",               # Type definitions fragment
    "header.riddl",                  # Command definitions fragment
    "page.riddl",                    # Group definitions fragment
    "application.riddl",             # Context fragment in full/ and includes/
    "context.riddl",                 # Context fragment in full/
}

# Files that are intentionally invalid for testing error handling
EXPECTED_FAILURES = {
    "invalid.riddl",      # Intentionally invalid RIDDL for testing
    "empty-case.riddl",   # Missing required user stories in epic and use case
}


def load_grammar() -> Lark:
    """Load the Lark grammar from file."""
    if not GRAMMAR_FILE.exists():
        print(f"Error: Grammar file not found: {GRAMMAR_FILE}")
        sys.exit(1)

    with open(GRAMMAR_FILE, "r", encoding="utf-8") as f:
        grammar_text = f.read()

    try:
        # Use Earley parser for maximum flexibility with ambiguous grammars
        parser = Lark(
            grammar_text,
            start="start",
            parser="earley",
            ambiguity="explicit",  # Show all parse trees if ambiguous
        )
        return parser
    except Exception as e:
        print(f"Error loading grammar: {e}")
        sys.exit(1)


def find_test_files() -> List[Path]:
    """Find all .riddl test files."""
    if not TEST_DIR.exists():
        print(f"Error: Test directory not found: {TEST_DIR}")
        sys.exit(1)

    files = list(TEST_DIR.rglob("*.riddl"))
    return sorted(files)


def is_include_fragment(filepath: Path) -> bool:
    """Check if file is an include fragment (not a standalone RIDDL file)."""
    return filepath.name in INCLUDE_FRAGMENTS


def is_expected_failure(filepath: Path) -> bool:
    """Check if file is expected to fail validation."""
    return filepath.name in EXPECTED_FAILURES


def validate_file(parser: Lark, filepath: Path, verbose: bool = False) -> Tuple[bool, Optional[str]]:
    """
    Validate a single RIDDL file against the grammar.

    Returns:
        (success, error_message) tuple
    """
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        # Skip empty files
        if not content.strip():
            return True, None

        # Attempt to parse
        tree = parser.parse(content)

        if verbose:
            print(f"Parse tree for {filepath.name}:")
            print(tree.pretty())

        return True, None

    except lark_exceptions.UnexpectedInput as e:
        return False, f"Unexpected input at line {e.line}, column {e.column}: {e}"
    except lark_exceptions.UnexpectedToken as e:
        return False, f"Unexpected token at line {e.line}, column {e.column}: expected {e.expected}, got {e.token}"
    except lark_exceptions.UnexpectedCharacters as e:
        return False, f"Unexpected character at line {e.line}, column {e.column}: {e.char}"
    except lark_exceptions.LarkError as e:
        return False, f"Parse error: {e}"
    except Exception as e:
        return False, f"Error reading file: {e}"


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Validate RIDDL files against EBNF-derived Lark grammar"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show detailed output for failures"
    )
    parser.add_argument(
        "--file", "-f",
        type=Path,
        help="Test a single file instead of all test files"
    )
    parser.add_argument(
        "--show-tree",
        action="store_true",
        help="Show parse tree for successful parses"
    )
    parser.add_argument(
        "--include-fragments",
        action="store_true",
        help="Include fragment files in validation (normally skipped)"
    )

    args = parser.parse_args()

    # Load grammar
    print(f"Loading grammar from: {GRAMMAR_FILE}")
    lark_parser = load_grammar()
    print("Grammar loaded successfully.\n")

    # Determine files to test
    if args.file:
        if not args.file.exists():
            print(f"Error: File not found: {args.file}")
            sys.exit(1)
        test_files = [args.file]
    else:
        test_files = find_test_files()
        print(f"Found {len(test_files)} test files in: {TEST_DIR}\n")

    # Validate files
    passed = []
    failed = []
    skipped_fragments = []
    expected_failures = []

    for filepath in test_files:
        relative_path = filepath.relative_to(TEST_DIR) if args.file is None else filepath

        # Handle include fragments
        if is_include_fragment(filepath) and not args.include_fragments:
            print(f"  ~ {relative_path} (include fragment, skipped)")
            skipped_fragments.append(filepath)
            continue

        # Handle expected failures
        if is_expected_failure(filepath):
            success, error = validate_file(lark_parser, filepath, verbose=args.show_tree)
            if not success:
                print(f"  ~ {relative_path} (expected failure)")
                expected_failures.append(filepath)
            else:
                print(f"  ! {relative_path} (expected to fail but passed)")
                failed.append((filepath, "Expected to fail but passed"))
            continue

        success, error = validate_file(lark_parser, filepath, verbose=args.show_tree)

        if success:
            print(f"  \u2713 {relative_path}")
            passed.append(filepath)
        else:
            print(f"  \u2717 {relative_path}")
            if args.verbose and error:
                print(f"    Error: {error}")
            failed.append((filepath, error))

    # Summary
    print(f"\n{'='*60}")
    print(f"Results: {len(passed)}/{len(test_files)} passed")
    if skipped_fragments:
        print(f"Skipped: {len(skipped_fragments)} include fragments")
    if expected_failures:
        print(f"Expected failures: {len(expected_failures)}")

    if failed:
        print(f"\nUnexpected failures ({len(failed)}):")
        for filepath, error in failed:
            relative_path = filepath.relative_to(TEST_DIR) if args.file is None else filepath
            print(f"  - {relative_path}")
            if error:
                # Truncate long error messages
                error_preview = error[:200] + "..." if len(error) > 200 else error
                print(f"    {error_preview}")

    # Return non-zero only for unexpected failures
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
