Update the existing `PROJECT_CONTEXT.md` into the definitive handoff and architecture document for this project.

Do NOT implement new production functionality as part of this task.

This is a documentation + architecture-context task so that development can continue on another machine without losing any understanding of what has already been investigated, implemented, tested, what remains, and what the original platform goal is.

The document must be detailed enough that a new developer/agent can read ONLY `PROJECT_CONTEXT.md` and understand the project from the beginning to the current state.

---

# 1. ORIGINAL PLATFORM GOAL

The overall product is intended to become an:

## FSS ISO 20022 PLATFORM

with the following major architecture:

```text
                         FSS ISO 20022 PLATFORM
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
           PARSER              VALIDATOR            BUILDER
              │                   │
           Prowide          ┌──────┴──────┐
              │              │             │
              │             XSD         Rule Engine
              │              │             │
              │              │       ┌─────┼─────┐
              │              │       │     │     │
              │              │      ISO  Scheme Bank
              │              │       │     │     │
              │              │       └─────┼─────┘
              │              │             │
              │              │      Rule Repository
              │              │             │
              └──────────────┼─────────────┘
                             │
                             ▼
                    Validation Response
                             │
                             ▼
                    Version Management
                             │
                       ┌─────┴─────┐
                       ▼           ▼
                  Version Diff   Impact
                       │           │
                       └─────┬─────┘
                             ▼
                    Canonical Payment
                          Model
                             │
               ┌─────────────┼─────────────┐
               ▼             ▼             ▼
          Transformation   APIs          PaymentOS
               │
          MT / MX / JSON
               
                             +
                        AI Layer
                             │
               ┌─────────────┼─────────────┐
               ▼             ▼             ▼
            Mapping       Explain       Analysis
```

Document this as the ORIGINAL TARGET ARCHITECTURE, not as something already completed.

Clearly distinguish:

* what exists today
* what has been investigated
* what has been prototyped
* what remains to be implemented
* what is still underspecified

Do not claim future components are already implemented.

---

# 2. ORIGINAL THREE CORE COMPONENTS

Document the intended first three core capabilities:

### Parser

Purpose:

Raw ISO 20022 XML
→ identify message
→ resolve appropriate model/schema
→ parse into Java/Prowide model

Prowide is currently being used as the underlying ISO 20022 model/parser infrastructure.

### Validator

Validator is broader than parsing.

It eventually consists of:

1. XSD structural validation
2. Semantic/business-rule validation
3. ISO rules
4. Scheme rules
5. Bank-specific rules
6. Rule repository
7. Validation response

Make clear that XSD validation alone is NOT the complete validator.

### Builder

The Builder is intended to construct/build ISO 20022 messages from structured Java/domain/canonical representations.

It should eventually support the reverse direction of the parser:

```text
Canonical / Java representation
        ↓
Builder
        ↓
ISO 20022 MX XML
```

Also investigate/document that the exact FSS Builder API is currently underspecified and has NOT yet been implemented.

Do not invent the final Builder API.

---

# 3. PHASE HISTORY

Create a chronological implementation history.

## PHASE 1 — Parser Orchestration

Document everything actually implemented.

Current pipeline:

```text
Raw XML
   ↓
ISOParser
   ↓
Message Identification
   ↓
ISOMessageIdentifier
   ↓
SchemaRegistry
   ↓
XSD Validation
   ↓
Prowide Model Parsing
   ↓
ISOParserResult
```

Document:

* `ISOParser`
* `ISOMessageIdentifier`
* `ProwideAdapter`
* `SchemaRegistry`
* `ISOParserResult`
* parser statuses
* Prowide boundary
* dynamic message routing
* no per-message `if/else`
* namespace extraction
* generated model resolution
* model parsing behavior

Explain that one generic parser entry point can route messages such as:

* pacs
* camt
* pain
* seev

without hardcoding individual message types.

Explain the Prowide mechanism that converts something such as:

```text
pacs.002.001.12
```

into the generated model class:

```text
MxPacs00200112
```

Do not modify Prowide internals.

---

# 4. PHASE 2 — XSD VALIDATION

Document the implemented validation architecture.

Explain:

```text
XML
 ↓
SAX/JAXP validation
 ↓
XMLFilter
 ↓
Document isolation
 ↓
XSD
 ↓
ValidationResult
```

Explain why `<AppHdr>` and `<Document>` handling matters.

Document:

* `ISOValidator`
* `ValidationResult`
* `ValidationError`
* schema resolution
* fatal SAX handling
* line/column diagnostics
* XSD PASS/FAIL
* Prowide gating

Important proven invariant:

```text
XSD FAIL
   ↓
Prowide model parsing is NOT executed
```

Also document the security hardening:

* `SafeXmlUtils.reader()`
* `SafeXmlUtils.schemaFactory()`
* XXE protections

---

# 5. FAILURE-LAYER TEST CORPUS

Document the generated mutation corpus and what each layer catches.

Include the concepts proven through the 15 mutation tests:

### XML syntax failures

Example:

* truncated XML

→ fatal SAX error

### Identification failures

Examples:

* missing namespace
* invalid namespace

→ `UNIDENTIFIABLE_INPUT`

### Schema failures

Examples:

* missing required elements
* wrong sequence
* invalid length
* invalid type
* invalid enum
* duplicate items

→ XSD `FAIL`

and Prowide is skipped.

### Model failures

Examples:

* valid message identity but generated Prowide model unavailable

→ `PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR`

Document that this corpus was specifically created to prove failure boundaries rather than merely test happy paths.

---

# 6. PHASE 3 — SEMANTIC VALIDATION

Document the transition from structural validation to business validation.

Core architecture:

```text
Raw XML
 ↓
ISOParser
 ↓
Identification
 ↓
XSD Structural Validation
 ↓
Prowide JAXB Model
 ↓
Semantic Validation
 ↓
Final Validation Result
 ↓
Builder / output layer
```

Explain:

### XSD validation answers:

"Is this XML structurally valid according to the schema?"

### Semantic validation answers:

"Does this structurally valid message obey the business/ISO/scheme/bank rules?"

Provide the demonstrated example:

```text
TxSts = RJCT
+
StsRsnInf missing
```

where:

```text
XSD = PASS
Semantic validation = FAIL
```

This distinction is fundamental to the platform.

---

# 7. SEMANTIC MODEL INVESTIGATION

Document the actual Prowide hierarchy inspected for:

```text
MxPacs00200112
    ↓
FIToFIPmtStsRpt
    ↓
TxInfAndSts
    ↓
TxSts
StsRsnInf
```

Document findings around:

* nested objects
* lists
* optional fields
* enums
* strings
* numeric values
* dates
* `XMLGregorianCalendar`
* JavaBeans naming
* collection initialization

Important discovered behavior:

Prowide JAXB list getters generally initialize collections rather than returning null.

Also document the JavaBeans naming issue:

```text
getFIToFIPmtStsRpt()
```

exposes the property as:

```text
FIToFIPmtStsRpt
```

This must be treated as a rule-authoring concern.

---

# 8. SEMANTIC ABSTRACTIONS IMPLEMENTED

Document these actual classes:

```text
SemanticRuleDefinition
SemanticViolation
SemanticValidationResult
TechnicalEvaluationError
RuleEvaluator
SemanticRuleEngine
SpELRuleEvaluator
SemanticRuleLoader
```

Explain each responsibility.

The architecture should remain:

```text
SemanticRuleEngine
        ↓
RuleEvaluator
        ↓
Expression engine
        ↓
Prowide model/context
        ↓
RuleResult
```

The semantic abstraction must NOT become permanently tied to SpEL.

SpEL is currently an implementation choice behind the `RuleEvaluator` abstraction.

---

# 9. RULE FORMAT

Document the current JSON rule structure.

Example:

```json
{
  "ruleId": "CBPR_PACS002_01",
  "messageType": "pacs.002",
  "versions": ["12"],
  "severity": "FATAL",
  "errorPath": "FIToFIPmtStsRpt/TxInfAndSts/StsRsnInf",
  "message": "Status Reason Information is mandatory when Transaction Status is RJCT.",
  "expression": "FIToFIPmtStsRpt == null ? true : FIToFIPmtStsRpt.txInfAndSts.?[txSts == 'RJCT' and stsRsnInf.empty].empty"
}
```

Important known technical debt:

`versions` exists in the rule definition but is not currently actively enforced by `SemanticRuleEngine`.

Document this explicitly.

---

# 10. SpEL INVESTIGATION

Document WHY an expression engine was considered.

Semantic rules eventually need to express things such as:

* A required when B has a value
* A required when B equals X
* mutually exclusive fields
* conditional mandatory fields
* collection-level rules
* ANY / ALL rules
* cross-record rules
* numeric comparisons
* date comparisons
* enum comparisons
* null/existence checks

SpEL was selected for the prototype because its collection selection/projection syntax is useful for deeply nested Prowide graphs.

Document the security model:

```text
SimpleEvaluationContext.forReadOnlyDataBinding()
```

and that tests demonstrated blocking of:

* arbitrary method invocation
* class access
* reflection/RCE-style expressions

Do not describe SpEL as universally "safe". State that safety depends on the restricted evaluation context and future configuration.

---

# 11. SEMANTIC HARDENING

Document the 9 semantic tests and their purpose:

1. valid semantic pass
2. XSD pass + semantic fail
3. missing optional field safety
4. empty collection safety
5. multiple collection entries
6. arbitrary method invocation blocked
7. class access blocked
8. date/numeric behavior
9. malformed rule definition

Exact verified result:

```text
9/9 tests passed
```

Document the important finding:

### Numeric

`BigDecimal` comparisons work natively.

### Dates

Prowide uses `XMLGregorianCalendar` in relevant areas.

Current semantic evaluation does NOT yet have the desired general date conversion layer.

Therefore date comparison requires the planned custom TypeConverter.

---

# 12. SEMANTIC INTEGRATION INTO ISOPARSER

Document the actual integration that has now been implemented.

Current intended execution:

```text
ISOParser.parse(...)
        ↓
identify
        ↓
resolve schema
        ↓
XSD validation
        ↓
if XSD FAIL
    semantic SKIPPED
        ↓
Prowide model parse
        ↓
if model unavailable
    semantic SKIPPED
        ↓
SemanticRuleEngine
        ↓
SemanticValidationResult
        ↓
ISOParserResult
```

Document the overloaded API:

```text
parse(String xml, SemanticRuleEngine semanticEngine)
```

and that the existing parsing path remains backward-compatible.

Document semantic statuses:

* PASS
* FAIL
* NOT_APPLICABLE
* TECHNICAL_ERROR
* SKIPPED

Document that semantic execution is only valid after:

```text
XSD PASS
+
Prowide model SUCCESS
```

---

# 13. RULE LOADER

Document `SemanticRuleLoader`.

Current responsibilities:

* load JSON rules
* use Gson
* validate mandatory fields
* fail fast on malformed rule definitions

Document that a future production Rule Repository is broader than the current JSON loader.

---

# 14. ISOParserResult STATE MODEL

Document the complete result model and invariants.

Important constraints:

```text
XSD FAIL
→ model SKIPPED
→ semantic SKIPPED
```

```text
UNIDENTIFIABLE INPUT
→ pipeline stops
```

```text
XSD PASS
+
model SUCCESS
→ semantic may execute
```

Also document the distinction between:

```text
semantic business violation
```

and:

```text
technical rule evaluation failure
```

via `SemanticViolation` versus `TechnicalEvaluationError`.

---

# 15. CURRENT TEST STATUS

Document the latest verified test state.

Latest known full-suite result:

```text
./gradlew clean test

BUILD SUCCESSFUL
483 tests
0 failures
0 errors
0 skipped
```

Also document targeted semantic tests:

```text
SpELSemanticValidationTest
9/9 passed
```

and:

```text
SemanticIntegrationTest
```

plus:

```text
SemanticRuleLoaderTest
```

Do not invent newer counts if the repository has changed.

If the actual current repository count differs, inspect the repository and record the actual current result.

---

# 16. CURRENT FILE/MODULE PLACEMENT

Document that the FSS-owned implementation currently sits inside:

```text
iso20022-core
```

and under packages such as:

```text
com.prowidesoftware.swift.model.mx.validation.*
```

List the FSS-owned classes currently introduced.

Also explicitly explain the architectural concern:

These classes are currently physically located inside a Prowide-owned module/package structure for convenience, but conceptually they belong to the FSS platform.

Do NOT move them during this task.

---

# 17. IMPORTANT ARCHITECTURAL QUESTION: SHOULD FSS CODE MOVE OUT?

Analyze and document the desired future direction.

Target concept:

```text
                 FSS Platform
                     │
              ┌──────┴──────┐
              │             │
         FSS Parser      FSS Validator
              │             │
              └──────┬──────┘
                     │
                Prowide Adapter
                     │
                  Prowide
```

The goal should eventually be:

```text
FSS-owned modules
        ↓
depend on
        ↓
Prowide
```

rather than:

```text
Prowide source/module
        contains
        ↓
FSS business platform
```

Explain the benefits:

* plugin/plugout
* independent versioning
* cleaner ownership
* easier replacement of Prowide
* reduced upstream contamination
* easier testing
* clearer licensing/dependency boundary
* easier deployment as an FSS product

But explicitly state:

DO NOT blindly move files yet.

First design the module/package boundary, dependency graph, public API, and migration plan.

---

# 18. SCHEMA REGISTRY TECHNICAL DEBT

Document the current known issue:

`SchemaRegistry` currently relies on filesystem-relative schema paths.

This creates deployment concerns for:

* JAR
* WAR
* container
* immutable deployment

Planned solution:

```text
Classpath resource loading
        ↓
optional external filesystem override
```

Do not implement this as part of the context-document task.

---

# 19. VERSION FIELD TECHNICAL DEBT

Document:

`SemanticRuleDefinition.versions`

exists but is not currently actively enforced during rule selection/evaluation.

This must eventually be resolved before claiming version-aware semantic validation is complete.

---

# 20. DATE TYPE CONVERSION

Document the next immediate technical task:

Implement a controlled FSS-owned TypeConverter for:

```text
XMLGregorianCalendar
        ↓
java.time representation
```

Then prove:

* `<`
* `>`
* `==`

date comparisons through semantic rules.

The converter must not weaken the SpEL security model.

---

# 21. FULL FUTURE PLATFORM ROADMAP

This is extremely important.

Add a roadmap section that extends from the current Phase 3 state all the way to the AI layer.

Use this structure:

## Phase 1 — Parser Orchestration

STATUS: COMPLETE

## Phase 2 — XSD Structural Validation

STATUS: COMPLETE

## Phase 3 — Semantic Validation Engine

STATUS: PROTOTYPE + CORE INTEGRATION COMPLETE

Remaining:

* TypeConverter
* version filtering
* broader rule applicability
* rule repository architecture
* production rule catalogue

## Phase 4 — Rule Repository

Build a proper repository for:

### ISO rules

Rules directly derived from ISO 20022 specifications.

### Scheme rules

Examples conceptually include scheme-specific constraints.

### Bank rules

Institution-specific validation rules.

Design rule precedence/priority.

Potential model:

```text
Rule Repository
      │
 ┌────┼────┐
 ▼    ▼    ▼
ISO Scheme Bank
```

Do NOT invent specific business rules without source evidence.

---

# 22. PHASE 5 — Validation Response

Create a stable external validation response contract.

Potential conceptual output:

```text
ValidationResponse
 ├── message identity
 ├── schema result
 ├── semantic result
 ├── violations
 ├── technical errors
 ├── rule IDs
 ├── severity
 ├── field/path
 └── version information
```

Do not finalize fields that have not been specified.

Clearly label this as design work.

---

# 23. PHASE 6 — VERSION MANAGEMENT

The platform must eventually understand ISO 20022 versions.

Include:

```text
Version Management
        │
        ├── Version Diff
        │
        └── Impact Analysis
```

Version Diff should eventually answer:

"What changed between message versions?"

Impact Analysis should eventually answer:

"What does this version change break or affect?"

Potential areas:

* XSD changes
* field additions/removals
* cardinality changes
* datatype changes
* enum changes
* semantic rule changes
* canonical model impact
* transformation impact

Do not claim these are implemented.

---

# 24. PHASE 7 — CANONICAL PAYMENT MODEL

The long-term platform should not expose every scheme/version-specific ISO structure directly to every downstream system.

Introduce a canonical payment model:

```text
ISO 20022 versions/schemes
            ↓
     Canonical Payment Model
            ↓
     downstream systems
```

The canonical model should eventually provide a stable internal representation across versions and message formats.

This is a major architectural phase and is NOT currently implemented.

---

# 25. PHASE 8 — TRANSFORMATION

Build transformation capabilities around the canonical model.

Target:

```text
Canonical Payment Model
        │
        ├── MT
        ├── MX
        └── JSON
```

Potential directions:

```text
MT → Canonical
MX → Canonical
JSON → Canonical

Canonical → MT
Canonical → MX
Canonical → JSON
```

Do not implement until canonical model boundaries are defined.

---

# 26. PHASE 9 — APIs

Expose the platform capabilities through APIs.

Potential services:

```text
Parser API
Validation API
Builder API
Version API
Transformation API
Rule API
```

Do not lock the final protocol yet.

The API layer should consume stable FSS-owned contracts rather than Prowide internals.

---

# 27. PHASE 10 — PAYMENTOS INTEGRATION

The platform eventually becomes a capability used by PaymentOS.

Conceptually:

```text
ISO Platform
      ↓
PaymentOS
```

Potential integration areas:

* validation
* parsing
* transformation
* canonical payments
* version compatibility
* message generation

Do not claim specific PaymentOS contracts until defined.

---

# 28. PHASE 11 — AI LAYER

AI is the final strategic layer on top of deterministic ISO infrastructure.

Architecture:

```text
                    AI Layer
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       Mapping      Explain      Analysis
```

### AI Mapping

Assist with:

* mapping between message structures
* mapping between ISO versions
* MT ↔ MX mapping assistance
* canonical model mapping
* field correspondence discovery

AI suggestions must ultimately be validated against deterministic rules.

### AI Explain

Explain:

* why validation failed
* what ISO rule was violated
* what field caused the issue
* what changed between versions
* why a transformation/mapping was selected

### AI Analysis

Analyze:

* validation trends
* message populations
* version migration impact
* recurring validation failures
* scheme/bank differences
* transformation issues

Important architectural principle:

```text
AI should augment deterministic ISO infrastructure.
AI should NOT replace XSD validation,
semantic rules, version logic, or canonical correctness.
```

The deterministic layer remains authoritative.

---

# 29. FUTURE COMPLETE ARCHITECTURE

Include the final conceptual architecture:

```text
                         FSS ISO 20022 PLATFORM
                                  │
        ┌───────────────┬─────────┼─────────┬───────────────┐
        ▼               ▼         ▼         ▼               ▼
     Parser          Validator  Builder  Version       Canonical
        │               │         │      Management       Model
     Prowide            │         │         │               │
        │          ┌────┴────┐    │    ┌────┴────┐          │
        │          │         │    │    │         │          │
        │         XSD    Semantic  │  Diff     Impact       │
        │                   │      │                         │
        │             ┌─────┼─────┐│                         │
        │             │     │     ││                         │
        │            ISO  Scheme Bank                         │
        │             │     │     │                          │
        │             └─────┼─────┘                          │
        │                   │                                │
        └───────────────────┴────────────────────────────────┘
                                  │
                                  ▼
                       Validation / Build Response
                                  │
                                  ▼
                        Canonical Payment Model
                                  │
                ┌─────────────────┼─────────────────┐
                ▼                 ▼                 ▼
          Transformation        APIs            PaymentOS
                │
           ┌────┼────┐
           ▼    ▼    ▼
          MT    MX   JSON
                                  │
                                  ▼
                              AI Layer
                                  │
                         ┌────────┼────────┐
                         ▼        ▼        ▼
                      Mapping  Explain  Analysis
```

---

# 30. IMMEDIATE NEXT TASK ORDER

After updating the context document, explicitly state the recommended engineering order.

Do NOT jump randomly into implementation.

Recommended order:

### Step 1

Complete Phase 3 date handling:

```text
XMLGregorianCalendar
→ controlled TypeConverter
→ SpEL
→ date comparison tests
```

### Step 2

Enforce semantic rule version applicability:

```text
SemanticRuleDefinition.versions
```

### Step 3

Review semantic rule matching:

* message type
* version
* severity
* applicability
* rule errors

### Step 4

Harden RuleLoader/RuleRepository boundary.

### Step 5

Decide and design the FSS/Prowide module separation.

Do not move files until this architecture is documented.

### Step 6

Refactor SchemaRegistry for classpath-first resource loading.

### Step 7

Prove semantic engine generically across:

* pacs
* camt
* pain
* seev

using actual available models/samples.

### Step 8

Define the Builder contract.

Only after the parser/validator contract is stable.

### Step 9

Design Version Management.

### Step 10

Design Canonical Payment Model.

### Step 11

Design Transformation.

### Step 12

Design APIs / PaymentOS integration.

### Step 13

Add AI capabilities on top.

---

# 31. STATUS LEGEND

At the top of the document add a clear status legend:

```text
[COMPLETE]
Implemented and tested.

[PROTOTYPE]
Implemented experimentally and proven, but not production-complete.

[INVESTIGATED]
Architecture/source behavior understood, implementation not complete.

[PLANNED]
Future work.

[UNDERSPECIFIED]
Requirement exists conceptually but contract/design is not yet defined.

[TECHNICAL DEBT]
Known issue that does not currently block the architecture.
```

Use these statuses throughout the document.

---

# 32. CRITICAL HANDOFF RULES

At the end of `PROJECT_CONTEXT.md`, add:

## Rules for Future Development

1. Do not modify Prowide internals unless explicitly required.
2. Keep FSS logic separated from Prowide-specific implementation.
3. Keep the Prowide dependency behind `ProwideAdapter` wherever practical.
4. Do not add per-message `if/else` routing.
5. XSD validation must remain independent of JAXB model parsing.
6. XSD FAIL must prevent semantic validation.
7. Semantic validation must only run after successful model parsing.
8. Keep semantic expression evaluation sandboxed.
9. Treat external rule definitions as potentially untrusted configuration.
10. Do not call the platform complete merely because one message works.
11. Prove generic behavior across multiple ISO business areas.
12. Do not build hundreds of rules before the rule architecture is stable.
13. Do not build all 35/36 target message schemas blindly without confirming the actual required scope.
14. Do not implement the Builder without defining its contract.
15. Keep deterministic validation authoritative over AI.
16. AI should assist mapping/explanation/analysis, not replace deterministic validation.
17. Preserve backward compatibility where practical.
18. Record test counts and exact Gradle commands in the context document.
19. Every architectural assumption must be verified against source code before implementation.
20. Maintain `PROJECT_CONTEXT.md` as the single source of truth for project handoff.

---

# 33. FINAL HANDOFF SUMMARY

End the document with a concise current-state summary:

```text
CURRENT STATE

Parser:
COMPLETE

XSD Validator:
COMPLETE

Semantic Engine:
PROTOTYPE + CORE INTEGRATION COMPLETE

Date Semantic Conversion:
PENDING

Rule Version Enforcement:
PENDING

Rule Repository:
PENDING

Schema Classpath Loading:
PENDING

FSS/Prowide Module Separation:
DESIGN PENDING

Builder:
UNDERSPECIFIED / NOT IMPLEMENTED

Version Management:
PLANNED

Canonical Payment Model:
PLANNED

Transformation:
PLANNED

APIs:
PLANNED

PaymentOS:
PLANNED

AI Mapping:
PLANNED

AI Explain:
PLANNED

AI Analysis:
PLANNED
```

Also record the latest verified test status from the actual repository.

IMPORTANT:

* This task is documentation only.
* Do not modify production architecture.
* Do not add dependencies.
* Do not move files.
* Do not implement TypeConverter.
* Do not implement Builder.
* Do not implement Version Management.
* Do not implement Canonical Model.
* Do not implement AI.

Before finalizing `PROJECT_CONTEXT.md`, inspect the current repository so that the document reflects the ACTUAL current code, not just the previous reports.

If any previous report conflicts with the current code, mark the discrepancy explicitly and use the current source/test results as authoritative.

After updating the document, provide:

1. Exact file path.
2. Sections added/updated.
3. Current implementation status.
4. Any discrepancies discovered.
5. Current test count/result.
6. Recommended immediate next task.

Do not perform any other code changes.
