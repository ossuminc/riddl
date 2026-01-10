# BAST Performance Benchmark Notes

## Current Status: INCOMPLETE

The current benchmarks compare **AST → binary (BAST write)** vs **text → AST (parsing)**.
This comparison is **meaningless** for evaluating the optimization.

## What We Actually Need to Compare

### Baseline (Current Approach)
```
text → AST (parsing)
```

### Optimized Approach (BAST)
```
binary → AST (BAST deserialization/loading)
```

## Critical Requirement

**BAST loading (binary → AST) MUST be significantly faster than parsing (text → AST)**

If `binary → AST` is not faster than `text → AST`, the entire BAST optimization is pointless.

Expected improvement: **10-50x faster** loading via BAST compared to parsing.

## Correct Benchmark Structure (TODO: Implement after BASTReader exists)

### Test 1: Full Round Trip Comparison
```scala
// Baseline: Parse RIDDL text
val parseStart = System.nanoTime()
val ast1 = parseRiddlText(riddlFile)
val parseTime = System.nanoTime() - parseStart

// One-time cost: Write BAST (not counted in comparison)
val bastBytes = writeBAST(ast1)

// Optimized: Load BAST
val loadStart = System.nanoTime()
val ast2 = readBAST(bastBytes)
val loadTime = System.nanoTime() - loadStart

// Critical metric
val speedup = parseTime.toDouble / loadTime.toDouble
assert(speedup > 10.0, s"BAST must be >10x faster, got ${speedup}x")
```

### Test 2: Large Project Import Simulation
```scala
// Simulate importing a large module that's used in many places
// This is the primary use case for BAST

// Baseline: Parse same file 100 times (simulating 100 imports)
val totalParseTime = (1 to 100).map { _ =>
  measureTime(parseRiddlText(riddlFile))
}.sum

// Optimized: Write BAST once, load 100 times
val writeTime = measureTime(writeBAST(ast))
val totalLoadTime = (1 to 100).map { _ =>
  measureTime(readBAST(bastBytes))
}.sum

// Real-world speedup includes one-time write cost
val realWorldSpeedup = totalParseTime / (writeTime + totalLoadTime)
```

### Test 3: Cold Start Performance
```scala
// Test that matters for developer experience:
// Time from "open file" to "AST ready"

// Baseline: Parse fresh .riddl file
val coldParseTime = measureColdStart {
  readFileFromDisk(riddlFile)
  parseRiddlText(fileContent)
}

// Optimized: Load .bast file
val coldLoadTime = measureColdStart {
  readFileFromDisk(bastFile)
  readBAST(fileContent)
}

// This includes disk I/O - binary files should load faster
```

## Current (Incomplete) Measurements

### What We Measured
- Text → AST: ~58ms (ReactiveBBQ)
- AST → binary: ~25ms (ReactiveBBQ)

### What This Tells Us
- BAST writing is relatively fast (good for one-time generation)
- But this doesn't validate the optimization goal

### What We Still Need
- **binary → AST time** (via BASTReader implementation)
- **Comparison: text → AST vs binary → AST**
- **Speedup factor must be >10x for large projects**

## Action Items

1. ✅ Implement BASTWriter (complete)
2. ⏳ Implement BASTReader (next step)
3. ⏳ Update benchmarks to compare text→AST vs binary→AST
4. ⏳ Verify >10x speedup requirement is met
5. ⏳ If speedup is insufficient, profile and optimize BASTReader

## Test Files to Use

- **Small**: ToDoodles (~6 nodes) - expect less speedup due to fixed overhead
- **Medium**: ReactiveBBQ Restaurant (~153 nodes) - should show clear speedup
- **Large**: Full ReactiveBBQ project (1000s of nodes) - should show maximum speedup

## Expected Results (Hypothetical)

| Project | Parse Time (text→AST) | BAST Load Time (binary→AST) | Speedup Factor |
|---------|----------------------:|-----------------------------:|---------------:|
| ToDoodles | ~60ms | ~6ms | 10x |
| ReactiveBBQ Restaurant | ~58ms | ~3ms | 19x |
| ReactiveBBQ Full | ~500ms | ~15ms | 33x |

**These are hypothetical targets - actual results depend on BASTReader implementation.**

## Why BAST Should Be Faster

1. **No parsing** - binary format reads directly into memory structures
2. **String interning** - pre-deduplicated strings reduce allocations
3. **Delta-encoded locations** - compact representation
4. **Memory layout** - sequential reads are cache-friendly
5. **No validation** - assume BAST is well-formed (already validated at write time)

## Failure Criteria

If BAST loading is NOT significantly faster than parsing:
- Re-evaluate format design
- Consider mmap-based lazy loading
- Profile and optimize reader
- Compare with alternative serialization (protobuf, flatbuffers, etc.)
