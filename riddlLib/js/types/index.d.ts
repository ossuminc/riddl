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
 * @typeParam T - The type of the parsed value (Root, Nebula, Token[], etc.)
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
 * Root AST node - the top-level result of parsing a complete RIDDL file.
 */
export interface RootAST {
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
 *   console.log("Domains:", result.value.domains);
 * } else {
 *   console.error("Errors:", RiddlAPI.formatErrorArray(result.errors));
 * }
 * ```
 */
export declare const RiddlAPI: {
  /**
   * Get the version string of the RIDDL library.
   */
  readonly version: string;

  /**
   * Parse a RIDDL source string and return the AST Root.
   *
   * @param source - The RIDDL source code to parse
   * @param origin - Optional origin identifier (e.g., filename) for error messages
   * @param verbose - Enable verbose failure messages (useful for debugging)
   * @returns Result object with parsed Root AST or errors
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
   * @returns Result object with parsed Root AST or errors
   */
  parseStringWithContext(
    source: string,
    origin: string,
    verbose: boolean,
    context: PlatformContext
  ): ParseResult<RootAST>;

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
};

