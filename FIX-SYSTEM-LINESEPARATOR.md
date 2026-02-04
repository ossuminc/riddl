# Fix System.lineSeparator() for Scala.js Compatibility

## Problem

`System.lineSeparator()` returns `\0` (null character) when compiled
to Scala.js. This null byte embeds into JavaScript string constants,
which terminates the string literal prematurely. The result is an
"Unterminated string literal" error from esbuild/vite when bundling
the JS output for Electron or browser use.

This was partially fixed in RIDDL 1.2.2 (`RiddlParserInput.scala`
changed to use `"\n"` directly), but several other call sites in
**shared** source remain, blocking Synapify from running.

## Root Cause

`System.lineSeparator()` is a JVM API. In Scala.js, it compiles to
a call that returns `\0` (null character) instead of `"\n"`. When
this value is interpolated into a string constant in the compiled JS
output, the null byte truncates the string at the JS parser level.

## Design: Centralize in PlatformContext

`PlatformContext` already has `def newline: String` (line 93 of
`PlatformContext.scala`). This is the correct abstraction point.
The pattern is:

1. **Trait** (`PlatformContext`) defines the contract: `def newline`
2. **Platform implementations** provide correct values:
   - JVM: `System.lineSeparator()` (correct `\r\n` on Windows)
   - JS: `"\n"` (safe, avoids null byte from broken JS impl)
   - Native: `System.lineSeparator()` (Scala Native implements
     this correctly — returns `"\r\n"` on Windows, `"\n"` on Unix)
3. **Shared code** accesses via `pc.newline` through the `given`
   instance, never calling `System.lineSeparator()` directly
4. The `given pc: PlatformContext` in each platform's package object
   ensures the correct implementation is resolved at compile time

## Changes Required

### 1. PlatformContext Trait (no change needed)

**File:** `utils/shared/src/main/scala/.../PlatformContext.scala`

The trait already defines the contract at line 93:

```scala
/** The newline character for this platform */
def newline: String
```

No change needed here.

### 2. Platform Implementations (2 changes, 1 confirmation)

#### JVM — CHANGE

**File:** `utils/jvm/src/main/scala/.../JVMPlatformContext.scala`
**Line 83:**

```scala
// Before:
override def newline: String = "\n"

// After:
override def newline: String = System.lineSeparator()
```

This restores correct platform-specific behavior on JVM (e.g.,
`\r\n` on Windows).

#### JavaScript — NO CHANGE (confirm correct)

**File:** `utils/js/src/main/scala/.../DOMPlatformContext.scala`
**Line 61:**

```scala
override def newline: String = "\n"
```

Already correct. Must NOT use `System.lineSeparator()` here — that
is the entire bug. Scala.js's implementation returns `\0` (null
character). Keeping `"\n"` is intentional and safe.

#### Native — CHANGE

**File:** `utils/native/src/main/scala/.../NativePlatformContext.scala`
**Line 95:**

```scala
// Before:
override def newline: String = "\n"

// After:
override def newline: String = System.lineSeparator()
```

Scala Native implements `System.lineSeparator()` correctly — it
checks `isWindows` at runtime and returns `"\r\n"` on Windows or
`"\n"` on Unix/macOS. This is safe to use here (unlike Scala.js).
See: [Scala Native javalib System.scala](https://github.com/scala-native/scala-native/blob/main/javalib/src/main/scala/java/lang/System.scala)

Note: RIDDL's own `ExceptionUtils.scala` in the native module
already calls `System.lineSeparator` at line 34, confirming it
works on this platform.

#### Given Instances (no changes needed)

Each platform's package object provides the `given` that wires
everything together:

- **JVM:** `given pc: PlatformContext = JVMPlatformContext()`
- **JS:** `given pc: PlatformContext = DOMPlatformContext()`
- **Native:** `given pc: PlatformContext = NativePlatformContext()`

Shared code doing `(using pc: PlatformContext)` then
`pc.newline` automatically gets the right value for the platform.

---

### 3. Messages.scala — CRITICAL (shared, compiles to JS)

**File:** `language/shared/src/main/scala/.../Messages.scala`
**Line 232:** `msgs.map(_.format).mkString(System.lineSeparator())`

This is an extension method on `Messages` (`List[Message]`). It
already has a `val nl: String = "\n"` at line 114 with a comment
explaining the Scala.js issue. Use it:

```scala
// Before:
def format: String = {
  msgs.map(_.format).mkString(System.lineSeparator())
}

// After:
def format: String = {
  msgs.map(_.format).mkString(nl)
}
```

This is the simplest fix — 1-line change, no signature changes,
uses the existing `nl` constant that was created for this purpose.

Alternatively, add `(using pc: PlatformContext)` and use
`pc.newline`, but that would propagate to all callers of `.format`
on message lists which is more invasive. The `nl` constant approach
is pragmatic and already documented.

---

### 4. StringHelpers.scala — CRITICAL (shared, compiles to JS)

**File:** `utils/shared/src/main/scala/.../StringHelpers.scala`
**Line 31:** `val nl = System.lineSeparator()`

Add `(using PlatformContext)` to `toPrettyString`:

```scala
// Before:
def toPrettyString(
  obj: Any,
  depth: Int = 0,
  paramName: Option[String] = None
): String = {
  val buf = new StringBuffer(1024)
  val nl = System.lineSeparator()
  ...

// After:
def toPrettyString(
  obj: Any,
  depth: Int = 0,
  paramName: Option[String] = None
)(using pc: PlatformContext): String = {
  val buf = new StringBuffer(1024)
  val nl = pc.newline
  ...
```

**Callers to update** (all in commands module, JVM/Native only —
all already have `PlatformContext` in scope via `using`, so the
given propagates automatically, no call-site changes needed):

- `Command.scala:68` — `toPrettyString(opt, 1)`
- `Command.scala:147` — `toPrettyString(...)`
- `CommonOptionsHelper.scala:272` — `toPrettyString(options, ...)`
- `DumpCommand.scala:37` — `toPrettyString(result, 1, None)`
- `FromCommand.scala:70` — `toPrettyString(newCO)`

**Tests:** `StringHelpersTest.scala` needs
`import com.ossuminc.riddl.utils.pc` so the `given PlatformContext`
is in scope. All ~15 test calls pick it up implicitly. The assertion
at line 225-226 that checks `System.lineSeparator()` should change
to `"\n"` or `pc.newline`.

---

### 5. FileBuilder.scala — CRITICAL (shared, compiles to JS)

**File:** `utils/shared/src/main/scala/.../FileBuilder.scala`
**Line 19:** `protected val new_line: String = System.lineSeparator()`

**Recommended approach — Scala 3 trait parameter:**

```scala
// Before:
trait FileBuilder {
  protected val new_line: String = System.lineSeparator()
  ...

// After:
trait FileBuilder(using pc: PlatformContext) {
  protected val new_line: String = pc.newline
  ...
```

**Implementors to update** (3 classes):

1. **OutputFile.scala** (jvm-native):
   ```scala
   // File: utils/jvm-native/src/main/scala/.../OutputFile.scala
   // Before:
   trait OutputFile extends FileBuilder {
   // After:
   trait OutputFile(using PlatformContext) extends FileBuilder {
   ```

2. **RiddlFileEmitter.scala** (shared, passes module):
   ```scala
   // File: passes/shared/src/main/scala/.../RiddlFileEmitter.scala
   // Before:
   case class RiddlFileEmitter(url: URL) extends FileBuilder {
   // After:
   case class RiddlFileEmitter(url: URL)(using PlatformContext)
       extends FileBuilder {
   ```

3. **FileBuilderTest.scala** (jvm test):
   ```scala
   // File: utils/jvm/src/test/scala/.../FileBuilderTest.scala
   // Add import: import com.ossuminc.riddl.utils.pc
   // Before:
   class TestFileBuilder extends FileBuilder {
   // After:
   class TestFileBuilder(using PlatformContext) extends FileBuilder {
   ```

**Note:** Check for further downstream classes that extend
`OutputFile` or `RiddlFileEmitter` — they may also need `using
PlatformContext` propagated. Most pass code already has
`PlatformContext` in scope.

**Simpler alternative** if trait parameters cause too many cascading
changes: Just change the default to `"\n"`:

```scala
protected val new_line: String = "\n"
```

This is safe on all platforms and requires zero downstream changes.
JVM code that truly needs `\r\n` on Windows can override `new_line`.
Less architecturally pure but pragmatic.

---

### 6. Command files — LOW PRIORITY (JVM/Native only)

These are JVM/Native only, so `System.lineSeparator()` works
correctly and won't produce null bytes. Fix for consistency with the
PlatformContext pattern but not urgent.

**Files:**
- `commands/shared/.../Command.scala:150`
- `commands/shared/.../HelpCommand.scala:50,55,59`
- `commands/shared/.../AboutCommand.scala:45`

All already have `using PlatformContext` in their call chain.
Replace `System.lineSeparator()` with `pc.newline`.

---

## Implementation Order

1. **JVMPlatformContext** and **NativePlatformContext** — change
   `newline` to `System.lineSeparator()` (JS stays as `"\n"`)
2. **Messages.scala:232** — use existing `nl` constant (1-line fix,
   immediate impact, no signature changes)
3. **StringHelpers.toPrettyString** — add `using PlatformContext`,
   use `pc.newline`, update tests
4. **FileBuilder** — add trait parameter or change default to `"\n"`
5. **Command files** — update for consistency

## Testing

After changes:
```bash
# Verify all platform tests pass
sbt tJVM

# Verify JS compilation produces clean output (no null bytes)
sbt "languageJS/fastLinkJS" "utilsJS/fastLinkJS"

# Optionally scan for null bytes in JS output:
python3 -c "
with open('path/to/fastopt/main.js', 'rb') as f:
    content = f.read()
    nulls = [i for i, b in enumerate(content) if b == 0]
    if nulls: print(f'NULL bytes at offsets: {nulls[:20]}')
    else: print('No null bytes found - clean!')
"

# Verify in Synapify (downstream consumer):
cd ../synapify
sbt clean fastLinkJS
npm run dev
```

## Version Impact

- If only changing `new_line` default and `Messages.nl` usage:
  **patch** bump (no API changes)
- If `FileBuilder` gets a trait parameter: **minor** bump (binary
  incompatible for implementors)
- If `StringHelpers.toPrettyString` gets `using PlatformContext`:
  **minor** bump (source compatible via given, but binary change)

## Audit: Remaining System.getProperty Calls

These `System.getProperty` calls also exist in shared code but are
handled with `Option(...)` null-safety:

- `URL.scala:159` — `Option(System.getProperty("user.dir"))
  .getOrElse("").drop(1)` — safe (returns empty string on JS)

The commands-only `System.getProperty` calls (InfoCommand,
PrettifyCommand) are JVM/Native only and safe.
