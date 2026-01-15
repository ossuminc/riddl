# Revised BAST Implementation Plan

## Goal
Make BAST caching fundamental to RIDDL parsing, like Python's .pyc files. Users shouldn't need to know about BAST - it should "just work".

## Key Design Decisions (For Review)

### 1. Move BAST Code into Language Module
- Move `BASTReader.scala`, `BASTWriter.scala`, `ByteBufferReader.scala`, `ByteBufferWriter.scala`, `VarIntCodec.scala`, `BinaryFormat.scala`, and `package.scala` constants into `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`
- Keep `bast/` directory only for `NOTEBOOK.md` documentation
- This eliminates circular dependency concerns since everything is in one module

### 2. Integrate BAST Check into TopLevelParser
When `TopLevelParser.parseInput()` or `TopLevelParser.parseURL()` is called:
1. If URL is a `.bast` file → load directly via BASTReader
2. If URL is a `.riddl` file:
   - Check for sibling `.bast` file (same name, different extension)
   - If `.bast` exists AND is newer than `.riddl` → load from `.bast`
   - Otherwise → parse `.riddl` normally
3. Optionally generate `.bast` after successful parse (controlled by `CommonOptions.autoGenerateBAST`)

### 3. Where to Add the Check
**Option A**: In `TopLevelParser.parseInput()` (recommended)
- Single entry point for all parsing
- Transparent to all callers
- Cleanest integration

**Option B**: In `RiddlParserInput.fromURL()`
- Earlier in the pipeline
- Would need to return different type for BAST vs text input

**Recommendation**: Option A - modify TopLevelParser

### 4. BASTWriter as Pass vs Direct Call
**Option A**: Keep BASTWriter as a Pass (current)
- Runs after validation passes
- More heavyweight but ensures AST is valid before caching

**Option B**: Direct serialization after parsing
- Lighter weight
- Cache even if validation fails (faster iteration)

**Recommendation**: Option B for auto-generation (cache parse result, not validation result)

## Implementation Steps

### Step 1: Move Files
Move from `bast/shared/src/main/scala/com/ossuminc/riddl/bast/` to `language/shared/src/main/scala/com/ossuminc/riddl/language/bast/`:
- `package.scala` (constants, node type tags)
- `BinaryFormat.scala`
- `VarIntCodec.scala`
- `ByteBufferReader.scala`
- `ByteBufferWriter.scala`
- `BASTReader.scala`
- `BASTWriter.scala`

Update package declarations from `com.ossuminc.riddl.bast` to `com.ossuminc.riddl.language.bast`

### Step 2: Update build.sbt
- Remove `bast` module from cross-project definitions
- Remove `bast` dependencies from other modules
- Keep `bast/` directory but don't compile it as a module

### Step 3: Modify TopLevelParser
Add BAST-aware parsing logic:
```scala
def parseInput(input: RiddlParserInput, options: CommonOptions): Either[Messages, Root] = {
  // Check for BAST cache first
  input.origin match {
    case url if url.path.endsWith(".bast") =>
      // Direct BAST load
      loadFromBAST(url)
    case url if url.path.endsWith(".riddl") =>
      // Check for sibling .bast
      val bastUrl = url.withExtension(".bast")
      if shouldUseBastCache(url, bastUrl) then
        loadFromBAST(bastUrl)
      else
        val result = parseRiddl(input)
        if options.autoGenerateBAST then
          generateBAST(result, bastUrl)
        result
    case _ =>
      parseRiddl(input)
  }
}
```

### Step 4: Move Tests
Move from `bast/jvm/src/test/scala/` to `language/jvm-native/src/test/scala/com/ossuminc/riddl/language/bast/`:
- `BASTWriterSpec.scala`
- `BASTRoundTripTest.scala`
- `BASTPerformanceTest.scala`
- `BASTLoaderTest.scala`

### Step 5: Update Commands Module
- Update imports in `BastGenCommand.scala` to use new package location
- `bast-gen` command still works but uses `language.bast` package

### Step 6: Clean Up
- Remove empty `bast/shared/`, `bast/jvm/`, etc. directories
- Keep only `bast/NOTEBOOK.md`
- Update `CLAUDE.md` to reflect new structure

## Decisions Made

**Include files**: NO - includes are always .riddl files and can occur in any container (not just Root/Domain), so BAST caching them would lead to ambiguities. Only top-level files get BAST caching.

## Remaining Questions

1. **Where to add the BAST check?**
   - **Option A (my recommendation)**: `TopLevelParser.parseInput()` - single entry point, transparent to all callers
   - **Option B**: `RiddlParserInput.fromURL()` - earlier in pipeline

2. **BAST generation timing** - when `autoGenerateBAST` is true:
   - **Option A**: After validation passes (ensures valid AST before caching)
   - **Option B (my recommendation)**: After parsing only (lighter weight, cache even invalid AST for faster iteration)

3. **Fallback behavior** - If BAST load fails (corrupt file):
   - **a) (my recommendation)**: Silently fall back to parsing .riddl
   - b) Warn and fall back
   - c) Error out

## Files to Keep in bast/ Directory
- `NOTEBOOK.md` - Engineering notebook documentation
- `KNOWN_ISSUES.md` - If it exists

Everything else moves to `language/shared/.../language/bast/`
