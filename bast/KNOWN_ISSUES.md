# BAST Known Issues

## Multiple Contents Fields Serialization Bug

**Status**: CRITICAL - Affects serialization/deserialization of larger files

**Discovered**: January 11, 2026

### Symptoms

- Small files (ToDoodles: 12 nodes) serialize/deserialize correctly
- Larger files (ShopifyCart: 510 nodes) fail during deserialization
- Error: "Invalid string table index: 1000019 (table size: 649)"
- The error occurs because the reader/writer become out of sync

### Root Cause

Some AST nodes have **multiple Contents fields**:

1. **`SagaStep`** (will not be removed)
   - `doStatements: Contents[Statements]`
   - `undoStatements: Contents[Statements]`

2. **`IfThenElseStatement`** (may be removed in future revision)
   - `thens: Contents[Statements]`
   - `elses: Contents[Statements]`

**The Problem**:

The current serialization architecture has a design flaw:

```scala
// BASTWriter.scala:658-660
private def writeSagaStep(ss: SagaStep): Unit = {
  writer.writeU8(NODE_HANDLER)
  writeLocation(ss.loc)
  writeIdentifier(ss.id)
  writeContents(ss.doStatements)    // Writes count immediately
  writeContents(ss.undoStatements)  // Writes count immediately
}
```

The `writeContents()` method writes the count:

```scala
private def writeContents[T <: RiddlValue](contents: Contents[T]): Unit = {
  writer.writeVarInt(contents.length)
  // Note: Individual elements are written by the main process() method during traversal
}
```

But the `traverse()` override only processes the main `.contents` field:

```scala
override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
  definition match {
    case branch: Branch[?] with WithMetaData =>
      process(branch, parents)
      parents.push(branch)
      branch.contents.foreach { value => traverse(value, parents) }  // Only .contents!
      parents.pop()
      writeMetadataCount(branch.metadata)
    case _ =>
      super.traverse(definition, parents)
  }
}
```

**Result**:
- Count for `doStatements` is written
- Count for `undoStatements` is written
- Items for `doStatements` are NEVER written (not in `.contents`)
- Items for `undoStatements` are NEVER written (not in `.contents`)
- Reader expects items after the count, reads garbage data
- Deserialization fails with "Invalid string table index"

### Affected Files

**Writer**: `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BASTWriter.scala`
- Lines 655-660: `writeSagaStep()`
- Lines 913-920: `writeIfThenElseStatement()`
- Line 1682-1685: `writeContents()`

**Reader**: `bast/shared/src/main/scala/com/ossuminc/riddl/bast/BASTReader.scala`
- Lines 1660-1673: `readContentsDeferred()`

### Impact

- ✅ **Works**: Files without SagaStep or IfThenElseStatement (e.g., ToDoodles)
- ❌ **Fails**: Files containing SagaStep or IfThenElseStatement (e.g., ShopifyCart)

### Performance Impact

When working correctly:
- **Small files (12 nodes)**: 1.8x speedup over parsing
- **Large files (510 nodes)**: 24.6x speedup over parsing (observed before deserialization failed)

### Solution Options

#### Option 1: Special-case traverse() for multi-Contents nodes

Extend the `traverse()` override to detect and handle nodes with multiple Contents fields:

```scala
override protected def traverse(definition: RiddlValue, parents: ParentStack): Unit = {
  definition match {
    case ss: SagaStep =>
      process(ss, parents)
      parents.push(ss)
      ss.doStatements.foreach { value => traverse(value, parents) }
      ss.undoStatements.foreach { value => traverse(value, parents) }
      parents.pop()
      writeMetadataCount(ss.metadata)

    case ite: IfThenElseStatement =>
      process(ite, parents)
      parents.push(ite)
      ite.thens.foreach { value => traverse(value, parents) }
      ite.elses.foreach { value => traverse(value, parents) }
      parents.pop()
      // No metadata for statements

    case branch: Branch[?] with WithMetaData =>
      // ... existing code
  }
}
```

**Pros**: Minimal changes, surgical fix
**Cons**: Must remember to update for any future multi-Contents nodes

#### Option 2: Refactor writeContents() to defer writing

Change `writeContents()` to not write anything immediately. Instead, track pending Contents writes and emit them during traversal.

**Pros**: More robust, handles any future cases
**Cons**: Significant refactoring, more complex state management

#### Option 3: Two-phase serialization

Separate count-writing from item-writing phases.

**Pros**: Clean separation of concerns
**Cons**: Requires complete redesign of serialization

### Recommended Fix

**Option 1** (special-case traverse) is recommended because:
1. Minimal code changes
2. Easy to understand and verify
3. Only 2 node types affected (possibly only 1 if IfThenElseStatement is removed)
4. Fast to implement and test

### Test Cases Needed

After fix:
1. ✅ Verify ToDoodles still works (regression test)
2. ✅ Verify ShopifyCart serializes/deserializes correctly
3. ✅ Create test specifically for SagaStep round-trip
4. ✅ Create test for IfThenElseStatement round-trip (if not removed)
5. ✅ Verify performance benchmarks still show speedup

### Related Files

- `bast/jvm/src/test/scala/com/ossuminc/riddl/bast/BenchmarkRunner.scala` - Performance benchmark
- `bast/jvm/src/test/scala/com/ossuminc/riddl/bast/TestRunner.scala` - Round-trip test
- `bast/shared/src/test/scala/com/ossuminc/riddl/bast/DeepASTComparison.scala` - Deep comparison utility

### Notes

- The issue does NOT affect metadata serialization (fixed in earlier session)
- The issue does NOT affect Include node preservation (working correctly)
- The issue does NOT affect location delta encoding (working correctly)
- The issue ONLY affects nodes with multiple Contents fields

---

**Last Updated**: January 11, 2026
**Severity**: High
**Priority**: Must fix before production use
