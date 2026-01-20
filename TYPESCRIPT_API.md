# RIDDL TypeScript API Reference

This document provides TypeScript type definitions and usage examples for the RIDDL JavaScript/TypeScript API.

## Installation

```bash
npm install /path/to/ossuminc-riddl-lib-*.tgz
```

## Type Definitions

### Result Types

All parsing methods return a result object with this structure:

```typescript
interface ParseResult<T> {
  succeeded: boolean;
  value?: T;           // Present when succeeded is true
  errors?: ErrorInfo[]; // Present when succeeded is false
}
```

### Error Information

```typescript
interface ErrorInfo {
  kind: string;        // e.g., "Error", "Warning", "MissingWarning"
  message: string;     // Human-readable error message
  location: Location;  // Where the error occurred
}
```

### Location Information

```typescript
interface Location {
  line: number;        // Line number (1-based)
  col: number;         // Column number (1-based)
  offset: number;      // Character offset from start of file
  source: string;      // Source file path or identifier
}
```

### Token Structure

Tokens returned from `parseToTokens()`:

```typescript
interface Token {
  text: string;        // The actual text content of the token
  kind: string;        // Token type: "Keyword", "Identifier", "Punctuation", etc.
  location: Location;  // Token position in source
}
```

Token kinds include:
- `Punctuation` - Brackets, parentheses, operators
- `QuotedString` - String literals
- `Readability` - Whitespace and formatting
- `Predefined` - Built-in types (String, Number, etc.)
- `Keyword` - RIDDL keywords (domain, context, type, etc.)
- `Comment` - Comments
- `LiteralCode` - Code literals
- `MarkdownLine` - Documentation
- `Identifier` - User-defined names
- `Numeric` - Number literals
- `Other` - Other tokens

### Root AST Structure

The result from `parseString()`:

```typescript
interface RootAST {
  kind: "Root";
  isEmpty: boolean;
  nonEmpty: boolean;
  domains: Domain[];
  location: Location;
}

interface Domain {
  id: string;
  kind: "Domain";
  isEmpty: boolean;
}
```

### Nebula Structure

The result from `parseNebula()`:

```typescript
interface NebulaAST {
  kind: "Nebula";
  isEmpty: boolean;
  nonEmpty: boolean;
  definitions: Definition[];
  location: Location;
}

interface Definition {
  kind: string;        // "Domain", "Context", "Type", "Entity", etc.
  id: string;         // Definition identifier
  isEmpty: boolean;
}
```

## API Reference

### RiddlAPI.parseString()

Parse a complete RIDDL source file.

```typescript
function parseString(
  source: string,
  origin?: string,
  verbose?: boolean
): ParseResult<RootAST>
```

**Parameters:**
- `source` - RIDDL source code to parse
- `origin` - Optional filename or identifier (default: "string")
- `verbose` - Enable verbose error messages (default: false)

**Returns:** `ParseResult<RootAST>`

**Example:**

```typescript
import { RiddlAPI } from '@ossuminc/riddl-lib';

const source = `
domain ShoppingCart is {
  context Cart is {
    type Item is String
  }
}
`;

const result = RiddlAPI.parseString(source, "shopping.riddl");

if (result.succeeded) {
  console.log("Domains found:", result.value.domains.length);
  result.value.domains.forEach(domain => {
    console.log(`Domain: ${domain.id}`);
  });
} else {
  console.error("Parse failed:");
  result.errors.forEach(err => {
    console.error(`  [${err.kind}] ${err.message}`);
    console.error(`    at line ${err.location.line}, column ${err.location.col}`);
  });
}
```

### RiddlAPI.parseNebula()

Parse a fragment of RIDDL definitions (partial file).

```typescript
function parseNebula(
  source: string,
  origin?: string,
  verbose?: boolean
): ParseResult<NebulaAST>
```

**Parameters:** Same as `parseString`

**Returns:** `ParseResult<NebulaAST>`

**Example:**

```typescript
const fragment = `
type UserId is String
type UserName is String
`;

const result = RiddlAPI.parseNebula(fragment);

if (result.succeeded) {
  console.log("Definitions:", result.value.definitions.map(d => d.id));
}
```

### RiddlAPI.parseToTokens()

Parse source into tokens for syntax highlighting.

```typescript
function parseToTokens(
  source: string,
  origin?: string,
  verbose?: boolean
): ParseResult<Token[]>
```

**Parameters:** Same as `parseString`

**Returns:** `ParseResult<Token[]>`

**Example:**

```typescript
const result = RiddlAPI.parseToTokens("domain Example is { ??? }");

if (result.succeeded) {
  result.value.forEach((token, index) => {
    console.log(`Token ${index}: "${token.text}" (${token.kind}) at line ${token.location.line}`);
  });
}
```

**Use Case - Syntax Highlighting:**

```typescript
function highlightRIDDL(source: string): string {
  const result = RiddlAPI.parseToTokens(source);

  if (!result.succeeded) {
    return source; // Return unhighlighted on error
  }

  // Map token kinds to CSS classes
  const tokenToClass: Record<string, string> = {
    Keyword: 'syntax-keyword',
    Identifier: 'syntax-identifier',
    Comment: 'syntax-comment',
    QuotedString: 'syntax-string',
    Numeric: 'syntax-number',
    // ... etc
  };

  // Build highlighted HTML
  let html = '';
  result.value.forEach(token => {
    const cssClass = tokenToClass[token.kind] || 'syntax-other';
    html += `<span class="${cssClass}">${escapeHtml(token.text)}</span>`;
  });

  return html;
}
```

### RiddlAPI.parseStringWithContext()

Parse with a custom platform context (advanced usage).

```typescript
function parseStringWithContext(
  source: string,
  origin: string,
  verbose: boolean,
  context: PlatformContext
): ParseResult<RootAST>
```

**Example:**

```typescript
const context = RiddlAPI.createContext(true, true, false);
const result = RiddlAPI.parseStringWithContext(
  source,
  "file.riddl",
  false,
  context
);
```

### RiddlAPI.createContext()

Create a custom platform context with specific options.

```typescript
function createContext(
  showTimes?: boolean,
  showWarnings?: boolean,
  verbose?: boolean
): PlatformContext
```

**Parameters:**
- `showTimes` - Enable timing information (default: false)
- `showWarnings` - Include warnings in messages (default: true)
- `verbose` - Enable verbose output (default: false)

### RiddlAPI.formatErrorArray()

Format error array as a human-readable string.

```typescript
function formatErrorArray(errors: ErrorInfo[]): string
```

**Example:**

```typescript
const result = RiddlAPI.parseString(badSource);

if (!result.succeeded) {
  const formatted = RiddlAPI.formatErrorArray(result.errors);
  console.error(formatted);
  // Output:
  // [Error] at line 1, column 10: Expected 'is' but found 'are'
  // [Warning] at line 5, column 1: Missing description
}
```

### RiddlAPI.errorsToStrings()

Convert errors to simple string array.

```typescript
function errorsToStrings(errors: ErrorInfo[]): string[]
```

**Example:**

```typescript
const result = RiddlAPI.parseString(badSource);

if (!result.succeeded) {
  const messages = RiddlAPI.errorsToStrings(result.errors);
  // messages = ["Expected 'is' but found 'are'", "Missing description"]

  messages.forEach(msg => console.error(msg));
}
```

### RiddlAPI.version

Get the RIDDL library version.

```typescript
const version: string
```

**Example:**

```typescript
console.log(`RIDDL version: ${RiddlAPI.version}`);
// Output: RIDDL version: 1.0.1-8-90a737de
```

## Complete Examples

### Simple Parser

```typescript
import { RiddlAPI } from '@ossuminc/riddl-lib';

function parseRIDDL(source: string): void {
  const result = RiddlAPI.parseString(source);

  if (result.succeeded) {
    console.log("✓ Parse successful");
    console.log(`  Domains: ${result.value.domains.length}`);
  } else {
    console.error("✗ Parse failed");
    console.error(RiddlAPI.formatErrorArray(result.errors));
  }
}
```

### IDE Integration

```typescript
interface DiagnosticInfo {
  severity: 'error' | 'warning';
  message: string;
  line: number;
  column: number;
}

function validateRIDDL(source: string): DiagnosticInfo[] {
  const result = RiddlAPI.parseString(source, "editor");

  if (result.succeeded) {
    return []; // No errors
  }

  return result.errors.map(err => ({
    severity: err.kind === 'Error' ? 'error' : 'warning',
    message: err.message,
    line: err.location.line,
    column: err.location.col
  }));
}
```

### Syntax Highlighter

```typescript
interface HighlightRange {
  start: number;
  end: number;
  tokenType: string;
}

function getRIDDLHighlights(source: string): HighlightRange[] {
  const result = RiddlAPI.parseToTokens(source);

  if (!result.succeeded) {
    return [];
  }

  return result.value.map(token => ({
    start: token.location.offset,
    end: token.location.endOffset,
    tokenType: token.kind
  }));
}
```

### Error Recovery

```typescript
function parseWithRecovery(source: string): RootAST | null {
  const result = RiddlAPI.parseString(source);

  if (result.succeeded) {
    return result.value;
  }

  // Try parsing as nebula if full parse fails
  const nebulaResult = RiddlAPI.parseNebula(source);

  if (nebulaResult.succeeded) {
    console.warn("Parsed as fragment (nebula) instead of complete file");
    // Convert nebula to minimal root or handle differently
    return null;
  }

  // Complete failure
  console.error("Parse failed completely:");
  console.error(RiddlAPI.formatErrorArray(result.errors));
  return null;
}
```

## TypeScript Configuration

For best results, add these to your `tsconfig.json`:

```json
{
  "compilerOptions": {
    "strict": true,
    "esModuleInterop": true,
    "moduleResolution": "node",
    "allowJs": true
  }
}
```

## Notes

- All return values are plain JavaScript objects (JSON-serializable)
- No Scala types leak into the JavaScript API
- All arrays are native JavaScript arrays, not Scala collections
- Error locations use 1-based line and column numbers (not 0-based)
- The AST is simplified - use `kind` field to determine node types
- For full AST access, consider using the Scala API directly

## See Also

- [NPM Packaging Guide](./NPM_PACKAGING.md)
- [RIDDL Language Documentation](https://riddl.tech)
- [GitHub Repository](https://github.com/ossuminc/riddl)
