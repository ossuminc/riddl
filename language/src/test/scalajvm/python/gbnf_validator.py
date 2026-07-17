#!/usr/bin/env python3
"""
GBNF Grammar Validator for RIDDL

Validates the generated GBNF grammar file for correctness:
- Syntax validation: every rule follows GBNF format
- Completeness: every referenced rule is defined
- Root rule exists
- No dangling references
- Override coverage: every override maps to an EBNF rule
- Freshness: committed GBNF matches regenerated output
- Character class validation: balanced brackets, valid ranges
- No EBNF-only constructs leaked through

Usage:
    python gbnf_validator.py [--verbose] [--file PATH]
"""

import re
import sys
import argparse
from pathlib import Path
from typing import Optional, Tuple
from collections import OrderedDict

# ── Paths ──

SCRIPT_DIR = Path(__file__).parent
LANGUAGE_DIR = SCRIPT_DIR.parent.parent.parent.parent
GBNF_FILE = (
    LANGUAGE_DIR
    / "shared/src/main/resources/riddl/grammar/riddl-grammar.gbnf"
)
EBNF_FILE = (
    LANGUAGE_DIR
    / "shared/src/main/resources/riddl/grammar/ebnf-grammar.ebnf"
)
OVERRIDES_FILE = SCRIPT_DIR / "gbnf_overrides.gbnf"

# Valid GBNF rule name pattern
RULE_NAME_RE = re.compile(r"^[a-z][a-z0-9-]*$")

# Rule definition pattern
RULE_DEF_RE = re.compile(
    r"^([a-z][a-z0-9-]*)\s*::=\s*(.+)$"
)

# EBNF-only constructs that should NOT appear in GBNF
EBNF_LEAKS = [
    (r"\(\*", "EBNF comment (* ... *)"),
    (r"\*\)", "EBNF comment (* ... *)"),
    (r"(?<![:])\s*=\s*(?!=)", "EBNF assignment (= instead of ::=)"),
    (r";\s*$", "EBNF semicolon terminator"),
    (r"\{[^}]*\}", "EBNF repetition { } (should be (...)*)"),
    (r"\[[^\]]*\](?!\*|\+|\?)",
     "possible EBNF optional [ ] (should be (...)?)"
     " — may be valid char class"),
]


class ValidationResult:
    """Collects validation results."""

    def __init__(self):
        self.errors = []
        self.warnings = []
        self.info = []

    def error(self, msg: str, line: int = 0):
        prefix = f"  Line {line}: " if line else "  "
        self.errors.append(f"{prefix}{msg}")

    def warn(self, msg: str, line: int = 0):
        prefix = f"  Line {line}: " if line else "  "
        self.warnings.append(f"{prefix}{msg}")

    def add_info(self, msg: str):
        self.info.append(f"  {msg}")

    @property
    def ok(self) -> bool:
        return len(self.errors) == 0


def parse_gbnf(
    content: str,
) -> Tuple[OrderedDict, list]:
    """Parse GBNF content into rules and comments.

    Returns:
        (rules dict {name: body}, list of (line_num, line))
    """
    rules = OrderedDict()
    lines_with_numbers = []

    for i, line in enumerate(content.split("\n"), 1):
        stripped = line.strip()
        lines_with_numbers.append((i, stripped))

        if not stripped or stripped.startswith("#"):
            continue

        m = RULE_DEF_RE.match(stripped)
        if m:
            name = m.group(1)
            body = m.group(2).strip()
            # Remove trailing comments
            comment_idx = body.find("  #")
            if comment_idx >= 0:
                body = body[:comment_idx].strip()
            rules[name] = body

    return rules, lines_with_numbers


def extract_references(body: str) -> set:
    """Extract rule name references from a GBNF body."""
    refs = set()
    i = 0
    while i < len(body):
        c = body[i]

        # Skip string literals
        if c == '"':
            i += 1
            while i < len(body) and body[i] != '"':
                if body[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue

        # Skip character classes
        if c == "[":
            i += 1
            if i < len(body) and body[i] == "^":
                i += 1
            while i < len(body) and body[i] != "]":
                if body[i] == "\\":
                    i += 1
                i += 1
            i += 1
            continue

        # Rule reference (lowercase with dashes)
        if c.isalpha() and c.islower():
            m = re.match(r"[a-z][a-z0-9-]*", body[i:])
            if m:
                refs.add(m.group(0))
                i += len(m.group(0))
                continue

        i += 1

    return refs


def validate_rule_name(
    name: str, line_num: int, result: ValidationResult
):
    """Validate a rule name follows GBNF conventions."""
    if not RULE_NAME_RE.match(name):
        result.error(
            f"Rule name '{name}' does not match GBNF "
            f"convention [a-z][a-z0-9-]*",
            line_num,
        )
    if "_" in name:
        result.error(
            f"Rule name '{name}' contains underscore "
            f"(use kebab-case)",
            line_num,
        )


def validate_char_class(
    char_class: str, line_num: int, result: ValidationResult
):
    """Validate a character class is well-formed."""
    if not char_class.startswith("["):
        return
    if "]" not in char_class:
        result.error(
            f"Unclosed character class: {char_class}",
            line_num,
        )
        return

    # Check for balanced brackets
    depth = 0
    for c in char_class:
        if c == "[":
            depth += 1
        elif c == "]":
            depth -= 1
    if depth != 0:
        result.error(
            f"Unbalanced brackets in char class: "
            f"{char_class}",
            line_num,
        )


def validate_balanced(
    body: str, line_num: int, result: ValidationResult
):
    """Check balanced parens and brackets in rule body."""
    paren_depth = 0
    in_string = False
    in_char_class = False

    for i, c in enumerate(body):
        if in_string:
            if c == "\\" and i + 1 < len(body):
                continue
            if c == '"':
                in_string = False
            continue

        if in_char_class:
            if c == "\\" and i + 1 < len(body):
                continue
            if c == "]":
                in_char_class = False
            continue

        if c == '"':
            in_string = True
        elif c == "[":
            in_char_class = True
        elif c == "(":
            paren_depth += 1
        elif c == ")":
            paren_depth -= 1
            if paren_depth < 0:
                result.error(
                    "Unmatched closing paren ')'",
                    line_num,
                )
                return

    if paren_depth != 0:
        result.error(
            f"Unbalanced parentheses (depth {paren_depth})",
            line_num,
        )
    if in_string:
        result.error("Unclosed string literal", line_num)
    if in_char_class:
        result.error("Unclosed character class", line_num)


def validate_no_ebnf_leaks(
    body: str,
    line_num: int,
    result: ValidationResult,
):
    """Check that no EBNF-only constructs leaked."""
    # Check for { } repetition (EBNF style)
    # But skip char classes and strings
    clean = re.sub(r'"[^"]*"', "", body)
    clean = re.sub(r"\[[^\]]*\]", "", clean)

    if re.search(r"\{[^}]*\}", clean):
        result.error(
            "EBNF-style repetition { } found "
            "(should be (...)*)",
            line_num,
        )

    if re.search(r";\s*$", body):
        result.error(
            "EBNF semicolon terminator found", line_num
        )


def validate_syntax(
    content: str, verbose: bool = False
) -> ValidationResult:
    """Run all syntax validations on GBNF content."""
    result = ValidationResult()
    rules, lines = parse_gbnf(content)

    result.add_info(f"Total rules: {len(rules)}")

    # 1. Root rule check
    if "root" not in rules:
        result.error("Missing required 'root' rule")
    else:
        result.add_info("Root rule: present")

    # 2. Validate each rule
    for line_num, line in lines:
        if not line or line.startswith("#"):
            continue

        m = RULE_DEF_RE.match(line)
        if not m:
            # Non-empty non-comment line that isn't a rule
            result.error(
                f"Line is not a valid GBNF rule: "
                f"{line[:60]}...",
                line_num,
            )
            continue

        name = m.group(1)
        body = m.group(2).strip()
        # Remove trailing comment
        comment_idx = body.find("  #")
        if comment_idx >= 0:
            body = body[:comment_idx].strip()

        validate_rule_name(name, line_num, result)
        validate_balanced(body, line_num, result)
        validate_no_ebnf_leaks(body, line_num, result)

        # Validate char classes in body
        for cc_match in re.finditer(r"\[[^\]]*\]", body):
            validate_char_class(
                cc_match.group(0), line_num, result
            )

    # 3. Completeness check — all references defined
    all_refs = set()
    for name, body in rules.items():
        refs = extract_references(body)
        all_refs |= refs

    # Remove self-references and built-in GBNF features
    defined = set(rules.keys())
    undefined = all_refs - defined

    if undefined:
        result.error(
            f"Undefined rule references: "
            f"{', '.join(sorted(undefined))}"
        )
    else:
        result.add_info("All rule references are defined")

    # 4. Unused rules (warnings only)
    # root is always used (entry point)
    used = {"root"} | all_refs
    unused = defined - used
    if unused:
        for u in sorted(unused):
            result.warn(f"Rule '{u}' is defined but "
                        f"never referenced")

    # 5. Check no single-quoted strings (GBNF uses
    #    double quotes only)
    for line_num, line in lines:
        if line.startswith("#"):
            continue
        # Look for single quotes outside char classes
        clean = re.sub(r"\[[^\]]*\]", "", line)
        clean = re.sub(r'"[^"]*"', "", clean)
        if "'" in clean:
            result.warn(
                f"Single quote found outside char class "
                f"(GBNF uses double quotes)",
                line_num,
            )

    return result


def validate_overrides(
    verbose: bool = False,
) -> ValidationResult:
    """Validate override rules against EBNF source."""
    result = ValidationResult()

    if not OVERRIDES_FILE.exists():
        result.error(
            f"Overrides file not found: {OVERRIDES_FILE}"
        )
        return result

    if not EBNF_FILE.exists():
        result.error(f"EBNF file not found: {EBNF_FILE}")
        return result

    # Parse overrides
    override_content = OVERRIDES_FILE.read_text(
        encoding="utf-8"
    )
    overrides = {}
    for m in re.finditer(
        r"^([a-z][a-z0-9-]*)\s*::=\s*(.+)$",
        override_content,
        re.MULTILINE,
    ):
        overrides[m.group(1)] = m.group(2).strip()

    # Parse EBNF rules
    ebnf_content = EBNF_FILE.read_text(encoding="utf-8")
    ebnf_rules = set()
    for m in re.finditer(r"^(\w+)\s*=", ebnf_content, re.MULTILINE):
        ebnf_rules.add(m.group(1))

    # Check each override has a matching EBNF rule
    for oname in overrides:
        # Convert kebab-case back to snake_case
        ebnf_name = oname.replace("-", "_")
        if ebnf_name not in ebnf_rules:
            result.error(
                f"Override '{oname}' has no matching "
                f"EBNF rule '{ebnf_name}'"
            )
        else:
            result.add_info(
                f"Override '{oname}' maps to "
                f"EBNF rule '{ebnf_name}'"
            )

    result.add_info(f"Override count: {len(overrides)}")

    # Validate override rule syntax
    for oname, obody in overrides.items():
        vr = ValidationResult()
        validate_balanced(obody, 0, vr)
        for e in vr.errors:
            result.error(
                f"Override '{oname}': {e}"
            )

    return result


def validate_freshness(
    verbose: bool = False,
) -> ValidationResult:
    """Check that committed GBNF matches regeneration."""
    result = ValidationResult()

    if not GBNF_FILE.exists():
        result.error(f"GBNF file not found: {GBNF_FILE}")
        return result

    try:
        # Import the converter
        sys.path.insert(0, str(SCRIPT_DIR))
        from ebnf_to_gbnf import convert
        import tempfile

        tmp = Path(tempfile.mktemp(suffix=".gbnf"))
        convert(
            EBNF_FILE,
            OVERRIDES_FILE,
            tmp,
            verbose=False,
        )

        existing = GBNF_FILE.read_text(encoding="utf-8")
        generated = tmp.read_text(encoding="utf-8")

        # Strip timestamps for comparison
        existing_clean = re.sub(
            r"# Generated:.*\n", "", existing
        )
        generated_clean = re.sub(
            r"# Generated:.*\n", "", generated
        )

        if existing_clean == generated_clean:
            result.add_info("GBNF is fresh (matches source)")
        else:
            # Find first difference
            e_lines = existing_clean.split("\n")
            g_lines = generated_clean.split("\n")
            for i, (e, g) in enumerate(
                zip(e_lines, g_lines), 1
            ):
                if e != g:
                    result.error(
                        f"GBNF differs at line {i}:\n"
                        f"    committed:  {e[:80]}\n"
                        f"    generated:  {g[:80]}"
                    )
                    break
            else:
                if len(e_lines) != len(g_lines):
                    result.error(
                        f"GBNF line count differs: "
                        f"{len(e_lines)} committed vs "
                        f"{len(g_lines)} generated"
                    )

        tmp.unlink(missing_ok=True)

    except Exception as e:
        result.error(f"Freshness check failed: {e}")

    return result


def validate_ebnf_coverage(
    verbose: bool = False,
) -> ValidationResult:
    """Check that every EBNF rule has a GBNF counterpart."""
    result = ValidationResult()

    if not GBNF_FILE.exists() or not EBNF_FILE.exists():
        result.error("Missing GBNF or EBNF file")
        return result

    # Parse EBNF rules
    ebnf_content = EBNF_FILE.read_text(encoding="utf-8")
    ebnf_rules = set()
    for m in re.finditer(
        r"^(\w+)\s*=", ebnf_content, re.MULTILINE
    ):
        ebnf_rules.add(m.group(1))

    # Parse GBNF rules
    gbnf_content = GBNF_FILE.read_text(encoding="utf-8")
    gbnf_rules, _ = parse_gbnf(gbnf_content)
    gbnf_names = set(gbnf_rules.keys())

    # Check coverage (convert EBNF names to kebab)
    missing = set()
    for ebnf_name in ebnf_rules:
        # Convert to expected GBNF name
        kebab = re.sub(
            r"([a-z0-9])([A-Z])", r"\1-\2", ebnf_name
        )
        kebab = kebab.replace("_", "-").lower()
        if kebab not in gbnf_names:
            missing.add(f"{ebnf_name} -> {kebab}")

    if missing:
        result.error(
            f"EBNF rules missing from GBNF: "
            f"{', '.join(sorted(missing))}"
        )
    else:
        result.add_info(
            f"All {len(ebnf_rules)} EBNF rules have "
            f"GBNF counterparts"
        )

    # Check for extra GBNF rules not in EBNF
    # (ws and ws1 are expected extras)
    expected_extras = {"ws", "ws1"}
    ebnf_kebab = set()
    for n in ebnf_rules:
        kebab = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", n)
        ebnf_kebab.add(kebab.replace("_", "-").lower())

    extra = gbnf_names - ebnf_kebab - expected_extras
    if extra:
        for e in sorted(extra):
            result.warn(
                f"GBNF rule '{e}' has no EBNF counterpart"
            )

    return result


def main():
    parser = argparse.ArgumentParser(
        description="Validate RIDDL GBNF grammar"
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Show detailed output",
    )
    parser.add_argument(
        "--file",
        "-f",
        type=Path,
        default=GBNF_FILE,
        help="GBNF file to validate",
    )
    parser.add_argument(
        "--skip-freshness",
        action="store_true",
        help="Skip freshness check",
    )

    args = parser.parse_args()

    if not args.file.exists():
        print(f"Error: GBNF file not found: {args.file}")
        sys.exit(1)

    content = args.file.read_text(encoding="utf-8")
    all_ok = True

    # 1. Syntax validation
    print("=" * 60)
    print("GBNF Grammar Validation")
    print("=" * 60)

    print("\n1. Syntax Validation")
    syntax_result = validate_syntax(content, args.verbose)
    for msg in syntax_result.info:
        print(msg)
    for msg in syntax_result.warnings:
        print(f"  WARNING: {msg}")
    for msg in syntax_result.errors:
        print(f"  ERROR: {msg}")
    if syntax_result.ok:
        print("  PASSED")
    else:
        print("  FAILED")
        all_ok = False

    # 2. Override validation
    print("\n2. Override Validation")
    override_result = validate_overrides(args.verbose)
    for msg in override_result.info:
        print(msg)
    for msg in override_result.errors:
        print(f"  ERROR: {msg}")
    if override_result.ok:
        print("  PASSED")
    else:
        print("  FAILED")
        all_ok = False

    # 3. EBNF coverage
    print("\n3. EBNF Coverage")
    coverage_result = validate_ebnf_coverage(args.verbose)
    for msg in coverage_result.info:
        print(msg)
    for msg in coverage_result.warnings:
        print(f"  WARNING: {msg}")
    for msg in coverage_result.errors:
        print(f"  ERROR: {msg}")
    if coverage_result.ok:
        print("  PASSED")
    else:
        print("  FAILED")
        all_ok = False

    # 4. Freshness check
    if not args.skip_freshness:
        print("\n4. Freshness Check")
        freshness_result = validate_freshness(args.verbose)
        for msg in freshness_result.info:
            print(msg)
        for msg in freshness_result.errors:
            print(f"  ERROR: {msg}")
        if freshness_result.ok:
            print("  PASSED")
        else:
            print("  FAILED")
            all_ok = False

    # Summary
    print("\n" + "=" * 60)
    if all_ok:
        print("All validations PASSED")
    else:
        print("Some validations FAILED")
        sys.exit(1)


if __name__ == "__main__":
    main()
