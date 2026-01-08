# RIDDL npm Package Building Guide

This guide explains how to build and use RIDDL JavaScript modules as local npm packages (without publishing to npmjs.com).

## Latest Updates (2026-01-08)

- ✅ **NEW: Full TypeScript support** - All Scala types converted to plain JavaScript objects
  - Scala `List` → JavaScript `Array`
  - Scala case classes → Plain JS objects with properties
  - All values are JSON-serializable
- ✅ **NEW: Error arrays instead of strings** - Errors returned as structured array of objects
- ✅ **JavaScript-friendly return types** - All methods return `{ succeeded: boolean, value?: object, errors?: Array<object> }`
- ✅ Added **RiddlAPI facade** with stable, non-minified method names
- ✅ Fixed minification issue - all API methods preserve their names in production builds
- ✅ Complete TypeScript type definitions and examples
- ✅ Current version: `1.0.1-8-90a737de-20260108-0749`
- ✅ Package size: ~244KB (production build)

**📘 See [TYPESCRIPT_API.md](./TYPESCRIPT_API.md) for complete TypeScript documentation and examples.**

## Quick Start

### 1. Build npm Packages

Build all JavaScript modules:
```bash
./scripts/pack-npm-modules.sh
```

Or build specific modules:
```bash
./scripts/pack-npm-modules.sh riddlLib utils
```

Available modules:
- `riddlLib` - Main RIDDL library
- `utils` - Utilities
- `passes` - Validation passes
- `diagrams` - Diagram generation
- `language` - Core language parser

### 2. Install in Your Project

```bash
cd /path/to/your/project
npm install /Users/reid/Code/ossuminc/riddl/npm-packages/ossuminc-riddl-lib-*.tgz
```

### 3. Use in Your Code

```typescript
import { RiddlAPI } from '@ossuminc/riddl-lib';

// Parse RIDDL source
const result = RiddlAPI.parseString("domain MyDomain is { ??? }");

if (result.succeeded) {
  console.log("Parse successful!");
  console.log("Domains:", result.value.domains);
  // result.value is a plain JS object with domains array
} else {
  console.error("Parse errors:");
  result.errors.forEach(err => {
    console.error(`  [${err.kind}] ${err.message} at line ${err.location.line}`);
  });
  // result.errors is an array of error objects
}

// Parse for syntax highlighting
const tokens = RiddlAPI.parseToTokens("domain Example is { ??? }");
if (tokens.succeeded) {
  tokens.value.forEach(token => {
    console.log(`${token.kind} at line ${token.location.line}`);
  });
  // tokens.value is a JavaScript Array of token objects
}

// Get version
console.log("RIDDL version:", RiddlAPI.version);
```

## Detailed Instructions

### Using the RiddlAPI Facade

The `@ossuminc/riddl-lib` package includes a **RiddlAPI** facade object with stable, non-minified method names (preserved even in production builds). This solves the issue where Scala.js minification made method names unusable from JavaScript.

**All methods return JavaScript-friendly result objects** with a `succeeded` boolean field instead of Scala's `Either` type.

#### Result Object Format

```typescript
interface ParseResult<T> {
  succeeded: boolean;
  value?: T;        // Present when succeeded is true
  errors?: string;  // Present when succeeded is false
}
```

#### Available Methods

All methods are exported with stable names:

- **`parseString(source, origin?, verbose?)`**
  - Parse a RIDDL source string
  - **Parameters:**
    - `source: string` - The RIDDL source code
    - `origin: string` - Optional origin identifier (default: "string")
    - `verbose: boolean` - Enable verbose error messages (default: false)
  - **Returns:** `{ succeeded: boolean, value?: Root, errors?: string }`

- **`parseStringWithContext(source, origin, verbose, context)`**
  - Parse with a custom platform context
  - **Parameters:**
    - `source: string` - The RIDDL source code
    - `origin: string` - Origin identifier
    - `verbose: boolean` - Enable verbose error messages
    - `context: PlatformContext` - Custom platform context
  - **Returns:** `{ succeeded: boolean, value?: Root, errors?: string }`

- **`parseNebula(source, origin?, verbose?)`**
  - Parse arbitrary RIDDL definitions (nebula)
  - A nebula is a collection of definitions that may not form a complete Root
  - **Parameters:** Same as `parseString`
  - **Returns:** `{ succeeded: boolean, value?: Nebula, errors?: string }`

- **`parseToTokens(source, origin?, verbose?)`**
  - Parse RIDDL source into tokens for syntax highlighting
  - Fast, lenient parsing without full validation
  - **Parameters:** Same as `parseString`
  - **Returns:** `{ succeeded: boolean, value?: Token[], errors?: string }`

- **`createContext(showTimes?, showWarnings?, verbose?)`**
  - Create a custom platform context with specific options
  - **Parameters:**
    - `showTimes: boolean` - Enable timing information (default: false)
    - `showWarnings: boolean` - Include warnings in messages (default: true)
    - `verbose: boolean` - Enable verbose output (default: false)
  - **Returns:** `PlatformContext`

- **`formatMessages(messages)`**
  - Format error/warning messages as a human-readable string
  - **Parameters:**
    - `messages: Messages` - The messages to format
  - **Returns:** `string` - Formatted messages separated by newlines

- **`version`** (property)
  - Get the RIDDL library version
  - **Returns:** `string` - Version number

#### Example Usage

```javascript
import { RiddlAPI } from '@ossuminc/riddl-lib';

// Basic parsing
const source = `
domain ShoppingCart is {
  type Item is String
}
`;

const result = RiddlAPI.parseString(source, "example.riddl");

if (result.succeeded) {
  console.log("✓ Parse successful");
  const root = result.value;
  // Work with the AST
} else {
  console.error("✗ Parse failed:");
  console.error(result.errors);
}

// Syntax highlighting
const tokens = RiddlAPI.parseToTokens(source);
if (tokens.succeeded) {
  tokens.value.forEach(token => {
    console.log(`${token.kind}: ${token.text}`);
  });
} else {
  console.error("Tokenization failed:", tokens.errors);
}

// Custom context
const context = RiddlAPI.createContext(true, true, false);
const result2 = RiddlAPI.parseStringWithContext(source, "test.riddl", false, context);
if (result2.succeeded) {
  console.log("Custom context parse successful");
}

// Version info
console.log(`Using RIDDL ${RiddlAPI.version}`);
```

#### TypeScript Usage

For TypeScript projects, you can define the result interface:

```typescript
interface ParseResult<T> {
  succeeded: boolean;
  value?: T;
  errors?: string;
}

import { RiddlAPI } from '@ossuminc/riddl-lib';

const result = RiddlAPI.parseString("domain Example is { ??? }") as ParseResult<any>;

if (result.succeeded) {
  console.log("Success:", result.value);
} else {
  console.error("Error:", result.errors);
}
```

### Building Packages

The build script does the following:
1. Gets the version from sbt (using git tags via dynver)
2. Builds optimized JavaScript using Scala.js `fullOptJS`
3. Creates `package.json` from template
4. Creates `README.md` with usage instructions
5. Runs `npm pack` to create a `.tgz` tarball
6. Outputs packages to `npm-packages/` directory

**Output location:** `npm-packages/`

**Package naming:** `ossuminc-<module-name>-<version>.tgz`

Example: `ossuminc-riddl-lib-1.0.1-7-a3a42f2e.tgz`

### Installing Packages

#### Option 1: Direct install (Recommended)

```bash
npm install /path/to/riddl/npm-packages/ossuminc-riddl-lib-*.tgz
```

#### Option 2: Add to package.json

```json
{
  "dependencies": {
    "@ossuminc/riddl-lib": "file:../riddl/npm-packages/ossuminc-riddl-lib-1.0.1-7-a3a42f2e.tgz"
  }
}
```

Then run:
```bash
npm install
```

#### Option 3: Wildcard in package.json (for latest)

```json
{
  "dependencies": {
    "@ossuminc/riddl-lib": "file:../riddl/npm-packages/ossuminc-riddl-lib-*.tgz"
  }
}
```

**Note:** Wildcard requires npm 7+ or using a symlink

### Updating After Changes

When you make changes to RIDDL and want to update your project:

1. **Rebuild the package:**
   ```bash
   cd /path/to/riddl
   ./scripts/pack-npm-modules.sh riddlLib
   ```

2. **Update in your project:**
   ```bash
   cd /path/to/your/project
   npm install /path/to/riddl/npm-packages/ossuminc-riddl-lib-*.tgz
   ```

   Or if using `file:` in package.json:
   ```bash
   rm -rf node_modules/@ossuminc
   npm install
   ```

### Development vs Production Builds

The script uses `fullOptJS` for production-optimized builds. For faster development builds:

**Edit the script** and change line 48:
```bash
# Production (slower build, smaller output):
sbt -Dsbt.supershell=false "project ${module_js}" fullOptJS

# Development (faster build, larger output):
sbt -Dsbt.supershell=false "project ${module_js}" fastOptJS
```

## Package Structure

Each package contains:

```
package/
├── main.js           # Compiled JavaScript
├── package.json      # npm package metadata
└── README.md         # Usage instructions
```

## Troubleshooting

### Package not found

**Error:** `ERROR: Package not found at /path/to/package.tgz`

**Solution:** Check that npm pack succeeded. Run manually:
```bash
cd riddlLib/js/target/scala-3.4.3/riddl-lib-opt
npm pack
```

### Version mismatch

**Error:** Wrong version in package.json

**Solution:** The version comes from git tags. Ensure:
```bash
git describe --tags --always
```

Returns expected version.

### Build fails

**Error:** `ERROR: Build failed for module`

**Solution:** Run sbt build manually to see errors:
```bash
sbt "project riddlLibJS" fullOptJS
```

## Adding New Modules

To add support for a new module:

1. **Create package.json.template:**
   ```bash
   cat > <module>/js/package.json.template << 'EOF'
   {
     "name": "@ossuminc/<module-name>",
     "version": "VERSION_PLACEHOLDER",
     "description": "RIDDL <Module> - JavaScript/TypeScript bindings",
     "main": "main.js",
     "type": "module",
     "exports": {
       ".": "./main.js"
     },
     "keywords": ["riddl"],
     "author": "Ossum Inc.",
     "license": "Apache-2.0",
     "repository": {
       "type": "git",
       "url": "https://github.com/ossuminc/riddl.git"
     }
   }
   EOF
   ```

2. **Add module to AVAILABLE_MODULES** in `scripts/pack-npm-modules.sh`:
   ```bash
   AVAILABLE_MODULES=("riddlLib" "utils" "passes" "diagrams" "language" "yourModule")
   ```

3. **Build:**
   ```bash
   ./scripts/pack-npm-modules.sh yourModule
   ```

## Alternative: Sharing Packages

### Via Git

Commit the `.tgz` files to a shared repository:

```bash
git add npm-packages/*.tgz
git commit -m "Add npm packages"
git push
```

Then team members can:
```bash
git pull
npm install npm-packages/ossuminc-riddl-lib-*.tgz
```

### Via File Share

Copy `npm-packages/` directory to a shared location (network drive, S3, etc.):

```bash
# On build machine:
cp npm-packages/*.tgz /shared/drive/riddl-packages/

# On developer machine:
npm install /shared/drive/riddl-packages/ossuminc-riddl-lib-*.tgz
```

## Comparison with Maven Local Repository

| Feature | Maven (.m2/repository) | npm pack |
|---------|----------------------|----------|
| Local install | `mvn install` | `npm pack` + `npm install` |
| Location | `~/.m2/repository` | Project directory |
| Version management | Automatic | Manual (via git tags) |
| Sharing | Automatic via .m2 | Manual (copy .tgz files) |
| CI/CD friendly | ✅ Yes | ✅ Yes (with file paths) |
| Multiple versions | ✅ Yes | ⚠️  Manual (different .tgz files) |

## See Also

- [npm pack documentation](https://docs.npmjs.com/cli/v9/commands/npm-pack)
- [Scala.js documentation](https://www.scala-js.org/)
- [RIDDL repository](https://github.com/ossuminc/riddl)
