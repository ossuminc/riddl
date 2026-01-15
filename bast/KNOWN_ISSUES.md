# BAST Known Issues

## Current Status (January 14, 2026)

**All known issues have been fixed. BAST serialization is working correctly.**

---

## Fixed Issues

### 1. Repository/Schema Tag Collision - FIXED (Jan 14, 2026)

**Problem**: Both Repository and Schema were using the same `NODE_REPOSITORY` tag (12), with a subtype byte to distinguish them. This caused the reader to misinterpret data when reading Repository nodes.

**Solution**:
- Added new `NODE_SCHEMA` tag (35) for Schema nodes
- Repository now writes: `NODE_REPOSITORY, loc, id, contents, metadata`
- Schema now writes: `NODE_SCHEMA, schemaKind, loc, id, data, links, indices, metadata`
- Reader dispatch handles both tags separately

**Files Modified**:
- `package.scala`: Added `NODE_SCHEMA: Byte = 35`
- `BASTWriter.scala`: Updated `writeRepository()` to not write subtype, updated `writeSchema()` to use `NODE_SCHEMA`
- `BASTReader.scala`: Split `readRepositoryOrSchemaNode()` into `readRepositoryNode()` and `readSchemaNode()`

### 2. Multiple Contents Fields Serialization - FIXED (Jan 13, 2026)

**Problem**: Nodes with multiple Contents fields (SagaStep, IfThenElseStatement, ForEachStatement) were not serializing correctly.

**Solution**:
- Added special cases in `traverse()` override for multi-Contents nodes
- Each Contents field now writes: count, items (interleaved)
- Added `NODE_SAGA_STEP` tag (34) to distinguish SagaStep from Handler

### 3. Statement/Handler Disambiguation - FIXED (Jan 13, 2026)

**Problem**: Reader couldn't reliably distinguish statements from handlers.

**Solution**:
- Added `STATEMENT_MARKER` (0xFF = 255) byte after NODE_HANDLER for statements
- Reader checks for marker before interpreting statement type

### 4. Branch Types without WithMetaData - FIXED (Jan 13, 2026)

**Problem**: Several types extend `Branch[T]` but NOT `WithMetaData`.

**Affected types**: Handler, OnClauses, Type, UseCase, Group, Output, Input

**Solution**: Added explicit traverse() cases for each of these types.

---

## Performance Results (All Tests Passing)

| File | Nodes | Parse Time | BAST Read | Speedup | Status |
|------|-------|------------|-----------|---------|--------|
| ToDoodles | 12 | ~6ms | ~6ms | ~1x | ✅ Working |
| ShopifyCart | 667 | ~77ms | ~4ms | **20.1x** | ✅ Working |

---

## Technical Notes

### Write/Read Order

```
Writer produces for Branch nodes:
  tag, nodeSpecificFields..., contentsCount, contentItems..., metadataCount, metadataItems...

Reader expects (after tag dispatch):
  nodeSpecificFields..., contentsCount+Items (via readContentsDeferred), metadataCount+Items
```

### Statement Format

```
Statement: NODE_HANDLER, STATEMENT_MARKER(255), stmtType(0-16), loc, stmtSpecificData...
Handler:   NODE_HANDLER, loc, id, contentsCount, contentItems..., metadataCount, metadataItems...
```

### Location Encoding

```
First location:  originString, offset, line, col
Delta location:  sameSourceFlag(0/1), [originString if flag=1], offsetDelta+1M, lineDelta+1K, colDelta+1K
```

---

**Last Updated**: January 14, 2026
**Severity**: None (all issues resolved)
**Priority**: N/A
