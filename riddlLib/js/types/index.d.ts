/**
 * TypeScript type definitions for @ossuminc/riddl-lib
 *
 * This module provides TypeScript/JavaScript bindings for the RIDDL language parser.
 * RIDDL (Reactive Interface to Domain Definition Language) is a specification language
 * for designing distributed, reactive, cloud-native systems using DDD principles.
 *
 * @packageDocumentation
 */

/**
 * Source location information for AST nodes and messages.
 * All values are 1-based (not 0-based).
 */
export interface Location {
  /** Line number (1-based) */
  line: number;
  /** Column number (1-based) */
  col: number;
  /** Character offset from start of source (0-based) */
  offset: number;
  /** End character offset (exclusive) */
  endOffset?: number;
  /** Source identifier (filename or "string") */
  source: string;
}

/**
 * Error or message information from parsing or validation.
 */
export interface ErrorInfo {
  /** Message severity/type */
  kind: 'Error' | 'SevereError' | 'Warning' | 'MissingWarning' | 'StyleWarning' | 'UsageWarning' | 'Info';
  /** Human-readable error message */
  message: string;
  /** Location in source where the error occurred */
  location: Location;
}

/**
 * Result from a parse operation.
 *
 * @typeParam T - The type of the parsed value (RootAST, Nebula, Token[], etc.)
 */
export interface ParseResult<T> {
  /** Whether parsing succeeded without errors */
  succeeded: boolean;
  /** The parsed value, present when succeeded is true */
  value?: T;
  /** Array of error objects, present when succeeded is false */
  errors?: ErrorInfo[];
}

/**
 * Lexical token from tokenization.
 */
export interface Token {
  /** The actual text content of the token */
  text: string;
  /** Token type/category (e.g., "Keyword", "Identifier", "LiteralString") */
  kind: string;
  /** Source location of the token */
  location: Location;
}

/**
 * Simplified domain representation in the AST.
 */
export interface Domain {
  /** Domain identifier name */
  id: string;
  /** Always "Domain" for domain nodes */
  kind: 'Domain';
  /** Whether the domain has no content */
  isEmpty: boolean;
}

/**
 * Opaque handle to a parsed RIDDL AST Root.
 *
 * Obtain via `parseString()`. Pass to `flattenAST()`,
 * `getDomains()`, or `inspectRoot()` to work with the AST.
 *
 * This is an opaque Scala object that cannot be inspected
 * directly from JavaScript. Use the accessor methods on
 * RiddlAPI to extract data.
 */
export interface RootAST {
  readonly __brand: 'RiddlRoot';
}

/**
 * Plain JavaScript summary of a Root AST node.
 * Returned by `inspectRoot()`.
 */
export interface RootInfo {
  /** Always "Root" for root nodes */
  kind: 'Root';
  /** Whether the root has no content */
  isEmpty: boolean;
  /** Whether the root has content */
  nonEmpty: boolean;
  /** Array of top-level domain definitions */
  domains: Domain[];
  /** Source location of the root */
  location: Location;
}

/**
 * Nebula AST node - a collection of arbitrary RIDDL definitions.
 * Used when parsing fragments that don't form a complete Root.
 */
export interface NebulaAST {
  /** Always "Nebula" for nebula nodes */
  kind: 'Nebula';
  /** Whether the nebula has no content */
  isEmpty: boolean;
  /** Whether the nebula has content */
  nonEmpty: boolean;
  /** Array of definitions in the nebula */
  definitions: DefinitionInfo[];
  /** Source location of the nebula */
  location: Location;
}

/**
 * Simplified definition information.
 */
export interface DefinitionInfo {
  /** Definition type (e.g., "Type", "Entity", "Context") */
  kind: string;
  /** Definition identifier name */
  id: string;
  /** Whether the definition has no content */
  isEmpty: boolean;
}

/**
 * Entry in a flat outline of RIDDL definitions.
 * Each entry represents a named definition with its depth in the hierarchy.
 */
export interface OutlineEntry {
  /** Definition type (e.g., "Domain", "Context", "Entity") */
  kind: string;
  /** Definition identifier name */
  id: string;
  /** Nesting depth (0 = top-level domain, 1 = context within domain, etc.) */
  depth: number;
  /** Line number in source (1-based) */
  line: number;
  /** Column number in source (1-based) */
  col: number;
  /** Character offset from start of source (0-based) */
  offset: number;
}

/**
 * Node in a recursive tree of RIDDL definitions.
 * Mirrors the hierarchical structure of the RIDDL model.
 */
export interface TreeNode {
  /** Definition type (e.g., "Domain", "Context", "Entity") */
  kind: string;
  /** Definition identifier name */
  id: string;
  /** Line number in source (1-based) */
  line: number;
  /** Column number in source (1-based) */
  col: number;
  /** Character offset from start of source (0-based) */
  offset: number;
  /** Child definitions nested within this definition */
  children: TreeNode[];
}

/**
 * Categorized validation messages from full validation.
 */
export interface ValidationMessages {
  /** Error messages (validation failures) */
  errors: ErrorInfo[];
  /** Warning messages */
  warnings: ErrorInfo[];
  /** Informational messages */
  info: ErrorInfo[];
  /** All messages combined */
  all: ErrorInfo[];
}

/**
 * Result from validation operation.
 */
export interface ValidationResult {
  /** Whether validation succeeded (no errors) */
  succeeded: boolean;
  /** Parse errors (syntax errors from parsing phase) */
  parseErrors: ErrorInfo[];
  /** Validation messages organized by severity */
  validationMessages: ValidationMessages;
}

/**
 * Build information about the RIDDL library.
 */
export interface BuildInfo {
  /** Module name */
  name: string;
  /** Version string */
  version: string;
  /** Scala version used to build */
  scalaVersion: string;
  /** sbt version used to build */
  sbtVersion: string;
  /** Module name (artifact name) */
  moduleName: string;
  /** Module description */
  description: string;
  /** Organization ID (e.g., "com.ossuminc") */
  organization: string;
  /** Organization name (e.g., "Ossum Inc.") */
  organizationName: string;
  /** Copyright holder */
  copyrightHolder: string;
  /** Full copyright string */
  copyright: string;
  /** License information */
  licenses: string;
  /** Project homepage URL */
  projectHomepage: string;
  /** Organization homepage URL */
  organizationHomepage: string;
  /** Human-readable build timestamp */
  builtAtString: string;
  /** Build instant as ISO string */
  buildInstant: string;
  /** Whether this is a snapshot build */
  isSnapshot: boolean;
}

/**
 * Platform context for customizing parsing behavior.
 * This is an opaque type - create instances via RiddlAPI.createContext()
 */
export interface PlatformContext {
  // Opaque type - internal Scala object
}

/**
 * Main API object for RIDDL parsing functionality.
 *
 * @example
 * ```typescript
 * import { RiddlAPI } from '@ossuminc/riddl-lib';
 *
 * const result = RiddlAPI.parseString("domain MyDomain is { ??? }");
 * if (result.succeeded) {
 *   // result.value is an opaque Root handle
 *   const info = RiddlAPI.inspectRoot(result.value);
 *   console.log("Domains:", info.domains);
 * }
 * ```
 */
export declare const RiddlAPI: {
  /**
   * Get the version string of the RIDDL library.
   */
  readonly version: string;

  /**
   * Parse a RIDDL source string and return an opaque Root handle.
   *
   * The returned `value` is an opaque Scala Root object. Use
   * `inspectRoot()` to get a plain JS summary, `getDomains()` to
   * extract domains, or pass it to `flattenAST()`.
   *
   * @param source - The RIDDL source code to parse
   * @param origin - Optional origin identifier (e.g., filename) for error messages
   * @param verbose - Enable verbose failure messages (useful for debugging)
   * @returns Result object with opaque Root handle or errors
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseString(`
   *   domain Banking is {
   *     context Accounts is {
   *       entity Account is { ??? }
   *     }
   *   }
   * `);
   * if (result.succeeded) {
   *   const info = RiddlAPI.inspectRoot(result.value);
   *   console.log("Domains:", info.domains.length);
   * }
   * ```
   */
  parseString(source: string, origin?: string, verbose?: boolean): ParseResult<RootAST>;

  /**
   * Parse a RIDDL source string with a custom platform context.
   *
   * @param source - The RIDDL source code to parse
   * @param origin - Origin identifier for error messages
   * @param verbose - Enable verbose failure messages
   * @param context - Custom platform context for I/O operations
   * @returns Result object with opaque Root handle or errors
   */
  parseStringWithContext(
    source: string,
    origin: string,
    verbose: boolean,
    context: PlatformContext
  ): ParseResult<RootAST>;

  /**
   * Flatten Include and BASTImport wrapper nodes from the AST.
   *
   * Recursively removes Include and BASTImport nodes, promoting their
   * children to the parent container. This makes accessor methods like
   * `getDomains()` return all definitions, including those originally
   * loaded from included/imported files.
   *
   * The Root is modified in-place and returned. The transformation is
   * one-way and irreversible.
   *
   * @param root - The opaque Root handle from parseString
   * @returns The same Root handle, with wrappers removed
   *
   * @example
   * ```typescript
   * const parseResult = RiddlAPI.parseString(source);
   * if (parseResult.succeeded) {
   *   const flattened = RiddlAPI.flattenAST(parseResult.value);
   *   const info = RiddlAPI.inspectRoot(flattened);
   *   console.log("All domains:", info.domains);
   * }
   * ```
   */
  flattenAST(root: RootAST): RootAST;

  /**
   * Get domain definitions from an opaque Root handle.
   *
   * @param root - The opaque Root handle from parseString
   * @returns Array of domain objects with id, kind, isEmpty
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseString("domain Foo is { ??? }");
   * if (result.succeeded) {
   *   const domains = RiddlAPI.getDomains(result.value);
   *   domains.forEach(d => console.log(d.id, d.kind));
   * }
   * ```
   */
  getDomains(root: RootAST): Domain[];

  /**
   * Inspect an opaque Root handle, returning a plain JS summary.
   *
   * @param root - The opaque Root handle from parseString
   * @returns Plain JS object with kind, isEmpty, domains, location
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseString("domain Foo is { ??? }");
   * if (result.succeeded) {
   *   const info = RiddlAPI.inspectRoot(result.value);
   *   console.log(info.kind);      // "Root"
   *   console.log(info.domains);   // [{id: "Foo", kind: "Domain", ...}]
   * }
   * ```
   */
  inspectRoot(root: RootAST): RootInfo;

  /**
   * Parse arbitrary RIDDL definitions (nebula).
   *
   * A nebula is a collection of RIDDL definitions that may not form a complete,
   * valid Root. This is useful for parsing fragments or partial files.
   *
   * @param source - The RIDDL source code to parse
   * @param origin - Optional origin identifier for error messages
   * @param verbose - Enable verbose failure messages
   * @returns Result object with parsed Nebula AST or errors
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseNebula(`
   *   type UserId is UUID
   *   type AccountNumber is String
   * `);
   * ```
   */
  parseNebula(source: string, origin?: string, verbose?: boolean): ParseResult<NebulaAST>;

  /**
   * Parse RIDDL source into a list of tokens for syntax highlighting.
   *
   * This is a fast, lenient parse that produces tokens without full validation.
   * Useful for editor syntax highlighting and quick feedback.
   *
   * @param source - The RIDDL source code to tokenize
   * @param origin - Optional origin identifier for error messages
   * @param verbose - Enable verbose failure messages
   * @returns Result object with array of tokens or errors
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseToTokens("domain Foo is { }");
   * if (result.succeeded) {
   *   result.value.forEach(token => {
   *     console.log(`${token.kind}: "${token.text}"`);
   *   });
   * }
   * ```
   */
  parseToTokens(source: string, origin?: string, verbose?: boolean): ParseResult<Token[]>;

  /**
   * Parse and validate RIDDL source, returning both syntax and semantic errors.
   *
   * This method runs the full validation pipeline:
   * 1. Parse the source to AST (syntax checking)
   * 2. Run SymbolsPass (build symbol table)
   * 3. Run ResolutionPass (resolve references)
   * 4. Run ValidationPass (semantic validation)
   *
   * @param source - The RIDDL source code to validate
   * @param origin - Optional origin identifier for error messages
   * @param verbose - Enable verbose failure messages
   * @param noANSIMessages - When true, ANSI color codes are not included in messages
   * @returns Validation result with categorized messages
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.validateString(`
   *   domain Banking is {
   *     context Accounts is {
   *       entity Account is {
   *         state current of Account.State is { ??? }
   *         handler input is { ??? }
   *       }
   *     }
   *   }
   * `);
   * if (!result.succeeded) {
   *   console.error("Errors:", result.validationMessages.errors);
   * }
   * ```
   */
  validateString(
    source: string,
    origin?: string,
    verbose?: boolean,
    noANSIMessages?: boolean
  ): ValidationResult;

  /**
   * Create a custom platform context with specific options.
   *
   * @param showTimes - Enable timing information in output
   * @param showWarnings - Include warnings in messages
   * @param verbose - Enable verbose output
   * @returns A new platform context with the specified options
   */
  createContext(
    showTimes?: boolean,
    showWarnings?: boolean,
    verbose?: boolean
  ): PlatformContext;

  /**
   * Get detailed build information about the RIDDL library.
   *
   * @returns Object with all build metadata
   */
  buildInfo: BuildInfo;

  /**
   * Format an array of error objects as a human-readable string.
   *
   * @param errors - Array of error objects from a parse result
   * @returns Formatted error string with one error per line
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.parseString("invalid riddl");
   * if (!result.succeeded) {
   *   console.error(RiddlAPI.formatErrorArray(result.errors));
   * }
   * ```
   */
  formatErrorArray(errors: ErrorInfo[]): string;

  /**
   * Convert errors array to a simple array of message strings.
   *
   * @param errors - Array of error objects from a parse result
   * @returns Array of error message strings
   */
  errorsToStrings(errors: ErrorInfo[]): string[];

  /**
   * Format build information as a human-readable string.
   * Provides the same output as the `riddlc info` command.
   *
   * @returns Formatted build information string
   */
  formatInfo: string;

  /**
   * Get a flat outline of all named definitions in RIDDL source.
   *
   * Returns a flat array of entries, each with kind, id, depth, and location.
   * Useful for building outline/table-of-contents views.
   *
   * @param source - The RIDDL source code to outline
   * @param origin - Optional origin identifier for error messages
   * @returns Result with array of OutlineEntry objects or errors
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.getOutline("domain D is { context C is { ??? } }");
   * if (result.succeeded) {
   *   result.value.forEach(entry => {
   *     console.log(`${"  ".repeat(entry.depth)}${entry.kind} ${entry.id}`);
   *   });
   * }
   * ```
   */
  getOutline(source: string, origin?: string): ParseResult<OutlineEntry[]>;

  /**
   * Get a recursive tree of all named definitions in RIDDL source.
   *
   * Returns a nested tree structure mirroring the RIDDL definition hierarchy.
   * Useful for building tree views or navigation panels.
   *
   * @param source - The RIDDL source code to process
   * @param origin - Optional origin identifier for error messages
   * @returns Result with array of top-level TreeNode objects or errors
   *
   * @example
   * ```typescript
   * const result = RiddlAPI.getTree("domain D is { context C is { ??? } }");
   * if (result.succeeded) {
   *   function printTree(nodes: TreeNode[], indent = 0) {
   *     nodes.forEach(n => {
   *       console.log(`${"  ".repeat(indent)}${n.kind} ${n.id}`);
   *       printTree(n.children, indent + 1);
   *     });
   *   }
   *   printTree(result.value);
   * }
   * ```
   */
  getTree(source: string, origin?: string): ParseResult<TreeNode[]>;
};
