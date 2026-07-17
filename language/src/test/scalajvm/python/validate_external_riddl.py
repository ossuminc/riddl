#!/usr/bin/env python3
"""
External RIDDL Repository Validator

Validates RIDDL files from external repositories (like riddl-examples)
against the EBNF grammar using TatSu.

This script finds entry point .riddl files by looking at the `input-file`
settings in .conf files, which specify the top-level files that include
other fragments.

Usage:
    python validate_external_riddl.py [--repo PATH] [--verbose]

Options:
    --repo PATH   Path to external RIDDL repository (default: riddl-examples)
    --verbose     Show detailed output
"""

import argparse
import re
import sys
from pathlib import Path
from typing import List, Set, Tuple, Optional

try:
    import tatsu
    from tatsu.exceptions import FailedParse, FailedToken, FailedPattern
except ImportError:
    print("Error: TatSu parser not installed. Install with: pip install TatSu")
    sys.exit(1)

from ebnf_preprocessor import preprocess_for_tatsu

# Paths relative to this script
SCRIPT_DIR = Path(__file__).parent
LANGUAGE_DIR = SCRIPT_DIR.parent.parent.parent.parent
EBNF_FILE = LANGUAGE_DIR / "shared" / "src" / "main" / "resources" / "riddl" / "grammar" / "ebnf-grammar.ebnf"
RIDDL_ROOT = LANGUAGE_DIR.parent

# Default external repository path (riddl-examples)
DEFAULT_EXTERNAL_REPO = RIDDL_ROOT.parent / "riddl-examples"

# Files with known issues that should be expected to fail
EXPECTED_FAILURES: Set[str] = {
    # Trello model has missing definitions and invalid syntax - needs AI regeneration
    "src/riddl/Trello/trello-riddl-model.riddl",
}


def load_grammar():
    """Load and compile the TatSu grammar from EBNF file."""
    if not EBNF_FILE.exists():
        print(f"Error: EBNF file not found: {EBNF_FILE}")
        sys.exit(1)

    print(f"Loading EBNF from: {EBNF_FILE}")
    with open(EBNF_FILE, "r", encoding="utf-8") as f:
        ebnf_content = f.read()

    print("Converting EBNF to TatSu format...")
    tatsu_grammar = preprocess_for_tatsu(ebnf_content)

    try:
        parser = tatsu.compile(tatsu_grammar)
        print("Grammar compiled successfully.\n")
        return parser
    except Exception as e:
        print(f"Error compiling grammar: {e}")
        sys.exit(1)


def find_entry_point_files(repo_path: Path) -> List[Path]:
    """
    Find entry point .riddl files by parsing .conf files.

    Entry points are specified in .conf files with:
        validate {
            input-file = "SomeFile.riddl"
        }

    The input-file is relative to the directory containing the .conf file.
    """
    entry_points: Set[Path] = set()

    # Find all .conf files
    conf_files = list(repo_path.rglob("*.conf"))

    # Pattern to match input-file = "filename.riddl"
    pattern = re.compile(r'input-file\s*=\s*"([^"]+\.riddl)"')

    for conf_file in conf_files:
        try:
            content = conf_file.read_text()
            matches = pattern.findall(content)
            for filename in matches:
                riddl_path = conf_file.parent / filename
                if riddl_path.exists():
                    entry_points.add(riddl_path.resolve())
        except Exception as e:
            print(f"Warning: Could not read {conf_file}: {e}")

    return sorted(entry_points)


def is_expected_failure(filepath: Path, repo_path: Path) -> bool:
    """Check if file is expected to fail."""
    if filepath.name in EXPECTED_FAILURES:
        return True
    try:
        rel_path = str(filepath.relative_to(repo_path))
        return rel_path in EXPECTED_FAILURES
    except ValueError:
        return False


def validate_file(parser, filepath: Path, verbose: bool = False) -> Tuple[bool, Optional[str]]:
    """Validate a single RIDDL file against the grammar."""
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        if not content.strip():
            return True, None

        parser.parse(content)
        return True, None

    except (FailedParse, FailedToken, FailedPattern) as e:
        return False, str(e)
    except Exception as e:
        return False, f"Error: {e}"


def main():
    """Main entry point."""
    arg_parser = argparse.ArgumentParser(
        description="Validate external RIDDL repository against EBNF grammar"
    )
    arg_parser.add_argument(
        "--repo", "-r",
        type=Path,
        default=DEFAULT_EXTERNAL_REPO,
        help=f"Path to external RIDDL repository (default: {DEFAULT_EXTERNAL_REPO})"
    )
    arg_parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Show detailed output for all files"
    )

    args = arg_parser.parse_args()
    repo_path = args.repo.resolve()

    if not repo_path.exists():
        print(f"Error: Repository path does not exist: {repo_path}")
        sys.exit(1)

    print(f"Validating RIDDL files from: {repo_path}")
    print("=" * 60)

    # Load grammar
    parser = load_grammar()

    # Find entry point files from .conf files
    files = find_entry_point_files(repo_path)

    if not files:
        print("No entry point .riddl files found (looked for input-file in .conf files)")
        sys.exit(1)

    print(f"Found {len(files)} entry point RIDDL files (from .conf files)\n")

    # Validate each file
    passed = 0
    failed = 0
    expected_failures = 0
    failures = []

    for filepath in files:
        rel_path = filepath.relative_to(repo_path)

        # Check if it's expected to fail
        if is_expected_failure(filepath, repo_path):
            success, error = validate_file(parser, filepath, args.verbose)
            if success:
                print(f"  ! {rel_path} (expected to fail but passed)")
                passed += 1
            else:
                print(f"  ~ {rel_path} (expected failure)")
                expected_failures += 1
            continue

        # Validate
        success, error = validate_file(parser, filepath, args.verbose)

        if success:
            print(f"  ✓ {rel_path}")
            passed += 1
        else:
            print(f"  ✗ {rel_path}")
            failed += 1
            failures.append((rel_path, error))

    # Summary
    print("\n" + "=" * 60)
    print(f"Results: {passed}/{len(files)} passed")
    if expected_failures > 0:
        print(f"Expected failures: {expected_failures}")

    if failures:
        print(f"\nUnexpected failures ({len(failures)}):")
        for rel_path, error in failures:
            # Show first few lines of error
            error_lines = error.split('\n')
            print(f"  - {rel_path}")
            for line in error_lines[:3]:
                if line.strip():
                    print(f"    {line[:100]}")
        return 1

    print("\n✓ All entry point files validated successfully!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
