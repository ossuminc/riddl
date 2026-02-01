#!/usr/bin/env python3
"""
RIDDL EBNF Grammar Validator using TatSu

Validates RIDDL files directly against the EBNF grammar using TatSu.
TatSu reads EBNF-like syntax and generates PEG parsers.

Usage:
    python ebnf_tatsu_validator.py [--verbose] [--file <path>]

Options:
    --verbose    Show detailed output for failures
    --file PATH  Test a single file instead of all test files
"""

import argparse
import sys
from pathlib import Path
from typing import List, Optional, Tuple

try:
    import tatsu
    from tatsu.exceptions import FailedParse, FailedToken, FailedPattern
except ImportError:
    print("Error: TatSu parser not installed. Install with: pip install TatSu")
    sys.exit(1)

from ebnf_preprocessor import preprocess_for_tatsu

# Paths relative to this script
SCRIPT_DIR = Path(__file__).parent
# Go up from python/ -> test/ -> src/ -> jvm/ -> language/ to find shared/src/...
LANGUAGE_DIR = SCRIPT_DIR.parent.parent.parent.parent
EBNF_FILE = LANGUAGE_DIR / "shared" / "src" / "main" / "resources" / "riddl" / "grammar" / "ebnf-grammar.ebnf"
# Go up one more to find riddl root
RIDDL_ROOT = LANGUAGE_DIR.parent

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
    "588included.riddl",             # Fragment included by 588.riddl
    "foo.riddl",                     # Fragment in issues/584/Foo/
}

# Files that are intentionally invalid for testing error handling
EXPECTED_FAILURES = {
    "invalid.riddl",      # Intentionally invalid RIDDL for testing
    "empty-case.riddl",   # Missing required user stories in epic and use case
}


def load_grammar() -> tatsu.grammars.Grammar:
    """Load and compile the TatSu grammar from EBNF file."""
    if not EBNF_FILE.exists():
        print(f"Error: EBNF file not found: {EBNF_FILE}")
        sys.exit(1)

    print(f"Loading EBNF from: {EBNF_FILE}")
    with open(EBNF_FILE, "r", encoding="utf-8") as f:
        ebnf_content = f.read()

    # Preprocess EBNF for TatSu compatibility
    print("Converting EBNF to TatSu format...")
    tatsu_grammar = preprocess_for_tatsu(ebnf_content)

    try:
        # Compile the grammar
        parser = tatsu.compile(tatsu_grammar)
        return parser
    except Exception as e:
        print(f"Error compiling grammar: {e}")
        print("\nGenerated TatSu grammar (first 200 lines):")
        for i, line in enumerate(tatsu_grammar.split('\n')[:200]):
            print(f"{i+1:4}: {line}")
        sys.exit(1)


def find_test_files() -> List[Path]:
    """Find all .riddl test files across all modules."""
    files = []

    # Find all input directories
    for input_dir in RIDDL_ROOT.glob("**/input"):
        if input_dir.is_dir():
            files.extend(input_dir.rglob("*.riddl"))

    return sorted(set(files))


def is_include_fragment(filepath: Path) -> bool:
    """Check if file is an include fragment (not a standalone RIDDL file)."""
    return filepath.name in INCLUDE_FRAGMENTS


def is_expected_failure(filepath: Path) -> bool:
    """Check if file is expected to fail validation."""
    return filepath.name in EXPECTED_FAILURES


def validate_file(parser, filepath: Path, verbose: bool = False) -> Tuple[bool, Optional[str]]:
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
        ast = parser.parse(content)

        if verbose:
            print(f"Parse succeeded for {filepath.name}")
            # TatSu AST can be large, so just indicate success

        return True, None

    except (FailedParse, FailedToken, FailedPattern) as e:
        # Extract useful error information
        error_msg = str(e)
        # Try to find line/column info
        if hasattr(e, 'line') and hasattr(e, 'col'):
            error_msg = f"Line {e.line}, column {e.col}: {e}"
        return False, error_msg
    except Exception as e:
        return False, f"Error: {e}"


def main():
    """Main entry point."""
    arg_parser = argparse.ArgumentParser(
        description="Validate RIDDL files against EBNF grammar using TatSu"
    )
    arg_parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show detailed output for failures"
    )
    arg_parser.add_argument(
        "--file", "-f",
        type=Path,
        help="Test a single file instead of all test files"
    )
    arg_parser.add_argument(
        "--include-fragments",
        action="store_true",
        help="Include fragment files in validation (normally skipped)"
    )
    arg_parser.add_argument(
        "--save-grammar",
        type=Path,
        help="Save the generated TatSu grammar to a file for debugging"
    )

    args = arg_parser.parse_args()

    # Optionally save the generated grammar for debugging
    if args.save_grammar:
        with open(EBNF_FILE, "r", encoding="utf-8") as f:
            ebnf_content = f.read()
        tatsu_grammar = preprocess_for_tatsu(ebnf_content)
        with open(args.save_grammar, "w", encoding="utf-8") as f:
            f.write(tatsu_grammar)
        print(f"Saved TatSu grammar to: {args.save_grammar}")

    # Load grammar
    parser = load_grammar()
    print("Grammar compiled successfully.\n")

    # Determine files to test
    if args.file:
        if not args.file.exists():
            print(f"Error: File not found: {args.file}")
            sys.exit(1)
        test_files = [args.file]
    else:
        test_files = find_test_files()
        print(f"Found {len(test_files)} test files\n")

    # Validate files
    passed = []
    failed = []
    skipped_fragments = []
    expected_failures = []

    for filepath in test_files:
        try:
            relative_path = filepath.relative_to(RIDDL_ROOT)
        except ValueError:
            relative_path = filepath

        # Handle include fragments
        if is_include_fragment(filepath) and not args.include_fragments:
            print(f"  ~ {relative_path} (include fragment, skipped)")
            skipped_fragments.append(filepath)
            continue

        # Handle expected failures
        if is_expected_failure(filepath):
            success, error = validate_file(parser, filepath, verbose=args.verbose)
            if not success:
                print(f"  ~ {relative_path} (expected failure)")
                expected_failures.append(filepath)
            else:
                print(f"  ! {relative_path} (expected to fail but passed)")
                failed.append((filepath, "Expected to fail but passed"))
            continue

        success, error = validate_file(parser, filepath, verbose=args.verbose)

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
            try:
                relative_path = filepath.relative_to(RIDDL_ROOT)
            except ValueError:
                relative_path = filepath
            print(f"  - {relative_path}")
            if error:
                # Truncate long error messages
                error_preview = error[:200] + "..." if len(error) > 200 else error
                print(f"    {error_preview}")

    # Return non-zero only for unexpected failures
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
