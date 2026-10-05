# PROJECT_CONTEXT

This document serves as a complete, self-contained handoff point for the FSS ISO 20022 Parser / Validator / Builder project. It details the original architectural goals, the implementation completed so far, the technical decisions made, and the remaining backlog.

---

## PART 1 — ORIGINAL OVERALL PROJECT GOAL

The original goal of this project is to build an **FSS-owned ISO 20022 ecosystem** consisting of:

```
PARSER + VALIDATOR + BUILDER
```

The intended end-to-end architecture is:

```
Raw ISO 20022 XML
        ↓
ISOParser
        ↓
Message Identification
        ↓
XSD Structural Validation
        ↓
Prowide JAXB Model
        ↓
Semantic Validation
        ↓
Final Validation Result
        ↓
Builder / Output Layer
```

**CRITICAL CONTEXT:** 
The objective is **NOT** to simply modify Prowide. Prowide is used solely as an underlying dependency to handle JAXB model generation and unmarshalling. FSS owns the orchestration layer, the validation framework, the semantic rules, the result contracts, and eventually the builder/output behavior. FSS code acts as a protective wrapper around Prowide.

---

## PART 2 — ORIGINAL DEVELOPMENT PLAN

The development plan was structured into phases to iteratively decouple from Prowide and build out the FSS orchestration layer.

### Phase 1 — Parser Orchestration
**Goal:** Build an FSS-owned generic parser facade capable of accepting an ISO 20022 XML message and dynamically routing it without hardcoded per-message branching.
**Responsibilities:**
- Accept raw XML.
- Identify ISO message type (`businessArea`, `function`, `variant`, `version`).
- Resolve corresponding XSD.
- Invoke structural validation.
- Invoke Prowide model parsing *only* when appropriate (i.e. XSD passes).
- Return a deterministic parser result.
- Isolate Prowide behind an adapter.
**Architectural Rule:** No hardcoded `if (pacs) ... else if (camt) ...` routing. The identifier token dynamically resolves schemas and models.

### Phase 2 — XSD Structural Validation
**Goal:** Validate XML structure independently from Prowide JAXB model parsing.
**Responsibilities:**
- Resolve appropriate XSD.
- Validate XML against XSD.
- Handle `AppHdr` and `<Document>` correctly (isolating the validation strictly to `<Document>`).
- Preserve namespace behavior and line/column diagnostics.
- Prevent Prowide parsing when XSD validation fails.
- Protect against XXE vulnerabilities.
*Why it is different from Semantic Validation:* XSD enforces syntax, cardinality, and data types (e.g. `Max35Text`). Semantic Validation enforces business logic (e.g. cross-field dependencies, conditional sums) that cannot be expressed in XSD.

### Phase 3 — Semantic Validation
**Goal:** Add business-rule validation beyond what XSD can express.
**Responsibilities:**
- Create an expression engine to evaluate rules against the generated Prowide Java model.
- Load externalized rules (JSON).
- Produce semantic violations.
- Protect against malicious expressions (sandboxing).
*Architectural Rule:* The expression engine must remain replaceable behind an interface (`RuleEvaluator`).

### Phase 4 — Builder / Output Layer
**Goal:** Provide an API to construct, manipulate, and serialize validated ISO 20022 messages.
**Status:** NOT IMPLEMENTED YET. The exact FSS-owned Builder contract is still underspecified. Do not invent the final API until requirements are defined.

---

## PART 3 — CURRENT REPOSITORY / PROWIDE STRUCTURE

The repository is currently based on a fork/clone of Prowide ISO 20022.

* **Root directory:** `/Users/GaneshvivekMannam/Desktop/parsing/prowide-iso20022`
* **Modules:** Gradle multi-project build.
* **`iso20022-core`:** Houses core Prowide mechanics AND our newly created FSS validation layer.
* **Model modules:** `model-acmt-types`, `model-pacs-mx`, etc., containing generated JAXB classes.
* **Schema location:** `schemas/{area}/{type}/{type}.xsd` (e.g., `schemas/pacs/pacs.002.001.12/pacs.002.001.12.xsd`).
* **Test location:** `iso20022-core/src/test/java/com/prowidesoftware/swift/model/mx/`
* **Semantic rules:** `iso20022-core/src/test/resources/semantic/rule.json`.

### Inventory of FSS-Created Components

| File | Current path | Existing or FSS-created | Modified? | Purpose | Should eventually belong to |
| ---- | ------------ | ----------------------- | --------- | ------- | --------------------------- |
| `ISOParser` | `iso20022-core/.../validation/` | FSS-created | Yes | Orchestrator pipeline | FSS External Layer |
| `ISOParserResult` | `iso20022-core/.../validation/` | FSS-created | Yes | Immutable result contract | FSS External Layer |
| `ISOMessageIdentifier` | `iso20022-core/.../validation/` | FSS-created | Yes | Message type token | FSS External Layer |
| `SchemaRegistry` | `iso20022-core/.../validation/` | FSS-created | Yes | XSD lookup & caching | FSS External Layer |
| `ISOValidator` | `iso20022-core/.../validation/` | FSS-created | Yes | SAX XSD streaming | FSS External Layer |
| `ValidationResult` | `iso20022-core/.../validation/` | FSS-created | Yes | Immutable error list | FSS External Layer |
| `ValidationError` | `iso20022-core/.../validation/` | FSS-created | Yes | Diagnostic detail | FSS External Layer |
| `ProwideAdapter` | `iso20022-core/.../validation/` | FSS-created | Yes | Prowide isolation layer | FSS Adapter / Plugin |
| `SemanticRuleDefinition` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | POJO for JSON rules | FSS External Layer |
| `SemanticRuleEngine` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Invokes rules on model | FSS External Layer |
| `SemanticRuleLoader` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Loads JSON from stream | FSS External Layer |
| `RuleEvaluator` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Engine abstraction | FSS External Layer |
| `SpELRuleEvaluator` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | SpEL implementation | FSS External Layer |
| `SemanticValidationResult` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Semantic result | FSS External Layer |
| `SemanticViolation` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Business rule violation | FSS External Layer |
| `TechnicalEvaluationError` | `iso20022-core/.../validation/semantic/` | FSS-created | Yes | Technical failure detail| FSS External Layer |
| `NamespaceReader` | `iso20022-core/.../` | Existing | Yes | Fixed inherited NS bug | Prowide (Core bugfix) |
| `MxParseUtils` | `iso20022-core/.../` | Existing | Yes | Added SAX filter safe-guard| Prowide |

---

## PART 4 — PHASE 1 IMPLEMENTATION ACTUALLY COMPLETED

The implementation successfully orchestrates validation and parsing:

```
ISOParser.parse(xml, semanticEngine)
        ↓
[1] ProwideAdapter.extractIdentifier(xml)
        ↓ (identifies namespace)
[2] SchemaRegistry.resolve(identifier)
        ↓ (caches and provides XSD)
[3] ISOValidator.validate(xml, identifier)
        ↓ (SAX validation filter over <Document>)
[4] ProwideAdapter.parseToModel(xml)
        ↓ (called ONLY if XSD passes or XSD is missing)
[5] semanticEngine.validate(parsedModel)
        ↓ (called ONLY if model was parsed successfully)
ISOParserResult (aggregates all phases)
```

**Key Behaviors:**
- XSD errors halt the pipeline before Prowide JAXB parsing is attempted.
- Missing XSDs (`SCHEMA_NOT_FOUND`) are non-blocking; the pipeline continues to model parsing.
- Semantic Engine errors (`TECHNICAL_ERROR`) are caught safely and do not crash the parser.

---

## PART 5 — MESSAGE IDENTIFICATION

`ProwideAdapter` passes the XML to Prowide's `MxParseUtils.identifyMessage(xml)`.
- It uses a fast streaming SAX/StAX parser to find the `<Document>` tag and reads its namespace (e.g., `urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12`).
- If `<Document>` has no namespace, it falls back to looking for an `AppHdr` `<MsgDefIdr>` (e.g., `head.001.001.01`).
- The resulting namespace string is passed to `ISOMessageIdentifier.fromNamespace()`.
- `ISOMessageIdentifier` strips the URN prefix and applies a regex (`([a-zA-Z][a-zA-Z0-9]{1,3})\.([0-9]{3})\.([0-9]{3})\.([0-9]{2,3})`) to extract:
  - `businessArea` = pacs
  - `messageFunction` = 002
  - `variant` = 001
  - `version` = 12

---

## PART 6 — GENERIC MESSAGE ROUTING

The parser natively handles `pacs`, `camt`, `pain`, `seev`, etc., without any hardcoded logic.
1. The `ISOMessageIdentifier` dynamically yields the type token (e.g., `pacs.002.001.12`).
2. `SchemaRegistry` dynamically resolves the XSD path: `schemas/pacs/pacs.002.001.12/pacs.002.001.12.xsd`.
3. Prowide's `MxId` dynamically resolves the Java class via reflection: 
   - It camelizes the token: `Pacs00200112`.
   - It attempts `Class.forName("com.prowidesoftware.swift.model.mx.MxPacs00200112")`.
4. If found, JAXB unmarshals into the generated class.

---

## PART 7 — XSD VALIDATION IMPLEMENTATION

`ISOValidator.validate(xml, identifier)` relies on the standard Java `javax.xml.validation.Validator`.
- It creates a `SAXSource` wrapped in an `XMLFilterImpl`.
- The filter skips all XML events until it encounters the `<Document>` element (ignoring `AppHdr` sibling wrappers).
- It safely forwards `startPrefixMapping()` so that inherited namespaces declared on outer envelopes (e.g. `<Message xmlns="...">`) are not lost.
- Errors are accumulated using a custom `ErrorHandler` into a `ValidationResult`, preserving precise line/column data.
- Fatal XML syntax errors are intercepted, converted to a `ValidationError("FATAL", ...)`, and caught gracefully.
- Factory configuration uses FSS-audited `SafeXmlUtils.schemaFactory()` which disables external entities (`http://javax.xml.XMLConstants/feature/secure-processing` = true), preventing XXE.

---

## PART 8 — FAILURE-LAYER MODEL

| Failure                    | Detection layer   | Result |
| -------------------------- | ----------------- | ------ |
| malformed XML              | validator         | `SchemaValidationStatus.FAIL` (ValidationResult contains "FATAL" error) |
| missing namespace          | identification    | `SchemaValidationStatus.UNIDENTIFIABLE_INPUT` |
| invalid namespace          | identification    | `SchemaValidationStatus.UNIDENTIFIABLE_INPUT` |
| unknown message type       | schema resolution | `SchemaValidationStatus.UNIDENTIFIABLE_INPUT` |
| missing XSD                | schema resolution | `SchemaValidationStatus.SCHEMA_NOT_FOUND` |
| XSD violation              | validator         | `SchemaValidationStatus.FAIL` |
| missing generated model    | Prowide           | `ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR` |
| JAXB parse failure         | Prowide           | `ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR` |
| semantic rule violation    | semantic engine   | `SemanticValidationStatus.FAIL` |
| semantic technical failure | semantic engine   | `SemanticValidationStatus.TECHNICAL_ERROR` |

---

## PART 9 — SECURITY HARDENING COMPLETED

- **XXE & External DTDs:** `SafeXmlUtils` ensures `XMLReader` and `SchemaFactory` have `FEATURE_SECURE_PROCESSING` enabled. External DTDs and schema inclusions are disabled.
- **SpEL Security:** Implemented via `SimpleEvaluationContext.forReadOnlyDataBinding()`. This strictly sandboxes expression execution:
  - Disables class instantiation (`new java.lang.ProcessBuilder()`).
  - Disables arbitrary method execution (e.g., `length()`, `getClass()`).
  - Only allows map lookups and safe property getter access against the Prowide Java models.
- Any attempt to invoke methods inside the expression yields a `TechnicalEvaluationError`, cleanly preventing RCE.

---

## PART 10 — TEST CORPUS AND MUTATION TESTING

Test suites like `ISOParserBoundaryTest`, `ISOParserHardeningAuditTest`, and `SemanticIntegrationTest` implement mutated payloads to prove architectural boundaries:
- `pacs.008.001.14_invalid.xml`: Mutated a Max35Text field to violate length. The XSD layer caught it, and the spy verified Prowide parsing was bypassed.
- Malformed tags, missing namespaces, and inherited namespaces were used to harden the `ISOMessageIdentifier` and `ISOValidator`.
- **The Critical Proof (XSD PASS + Semantic FAIL):** 
  - XML payload provided a `pacs.002.001.12` that passed XSD.
  - A semantic rule was applied checking `FIToFIPmtStsRpt?.grpHdr?.msgId == 'VALID'`.
  - The actual message ID was `MSG123`.
  - The model parsed successfully, but `SemanticRuleEngine` correctly failed the message, proving that Semantic Validation can reject syntactically valid models based on business constraints.

---

## PART 11 — PHASE 3 MODEL INVESTIGATION

Hierarchy for `pacs.002.001.12` investigated:
```
MxPacs00200112 (Root)
 └─ fiToFIPmtStsRpt (FIToFIPaymentStatusReportV12)
     ├─ grpHdr (GroupHeader101)
     │   └─ msgId (String)
     └─ txInfAndSts (List<PaymentTransaction130>)
         ├─ orgnlEndToEndId (String)
         └─ txSts (TransactionGroupStatus3Code - Enum)
```
- JAXB generates standard JavaBean getters/setters (`getFIToFIPmtStsRpt()`).
- Optional fields that are absent return `null`.
- Lists (`TxInfAndSts`) initialize as empty lists (`new ArrayList<>()`) or are populated automatically by JAXB if data is present.
- Enums (`TransactionGroupStatus3Code`) are strongly typed.
- Date fields use `XMLGregorianCalendar`.

---

## PART 12 — SEMANTIC VALIDATION ARCHITECTURE

The architecture uses an explicit separation of concerns:
```
SemanticRuleEngine
        ↓
RuleEvaluator (Interface)
        ↓
SpELRuleEvaluator (Implementation)
        ↓
Prowide JAXB Model
        ↓
SemanticValidationResult
```
**Classes:**
- `SemanticRuleDefinition`: Plain POJO mapping to the JSON config.
- `SemanticRuleEngine`: Coordinates rule matching and evaluation counting.
- `RuleEvaluator`: Interface allowing SpEL to be swapped out if needed (e.g., for JEXL or Groovy).
- `SemanticViolation`: Immutable record of a rule failure.
- `TechnicalEvaluationError`: Immutable record of an expression crash.
- `SemanticRuleLoader`: Reads JSON arrays and applies fail-fast validation.

---

## PART 13 — WHY SPEL WAS CHOSEN

SpEL (Spring Expression Language) was chosen over alternatives like JEXL because:
1. **Safe Navigation (`?.`)**: Deep object graphs common in ISO 20022 are easy to query without `NullPointerExceptions`.
2. **Collection Projections/Selections (`.?[...]`)**: Extremely powerful for querying nested transaction lists (e.g. "Find all transactions where status = RJCT").
3. **Sandboxing**: `SimpleEvaluationContext` provides an out-of-the-box, iron-clad sandbox for read-only data binding, preventing RCE natively without complex custom `SecurityManager` implementations.
4. **Familiarity**: FSS developers generally use Spring; SpEL is natively understood.

---

## PART 14 — CURRENT SPEL IMPLEMENTATION

- **Dependency:** `org.springframework:spring-expression` (latest).
- **Evaluation Context:** `SimpleEvaluationContext.forReadOnlyDataBinding().build()`.
- **Security:** Arbitrary method invocation is strictly blocked. Using methods like `.length()` causes a SpEL parse error, which is caught and surfaced as a `TechnicalEvaluationError`.
- **Rule execution:** The root object is the Prowide `AbstractMX` model (e.g. `MxPacs00200112`), meaning fields are accessed directly by their Java property names (e.g. `FIToFIPmtStsRpt` because getter is `getFIToFIPmtStsRpt()`).

---

## PART 15 — CURRENT RULE FORMAT

JSON format mapped by `SemanticRuleLoader`:
```json
[
  {
    "ruleId": "CBPR_PACS002_01",
    "messageType": "pacs.002",
    "versions": ["12", "14"], // (Defined in POJO, logic not yet enforced)
    "severity": "FATAL",
    "errorPath": "GrpHdr/MsgId",
    "expression": "FIToFIPmtStsRpt?.grpHdr?.msgId == 'MSG123'",
    "message": "Invalid MsgId"
  }
]
```
The rule `CBPR_PACS002_01` simply verifies that the `MsgId` equals `MSG123` to prove integration pipeline capabilities. 

---

## PART 16 — XSD PASS / SEMANTIC FAIL PROOF

**Integration Proof:** `SemanticIntegrationTest.testXsdPass_ModelSuccess_SemanticFail()`
- **Input:** valid `pacs.002.001.12` XML with `MsgId` = `MSG123`.
- **XSD Phase:** XSD limits `MsgId` to 35 characters (`Max35Text`). `MSG123` is 6 chars, so XSD `PASS`.
- **Model Phase:** Prowide correctly parses to `MxPacs00200112`.
- **Semantic Phase:** A rule `FIToFIPmtStsRpt?.grpHdr?.msgId == 'VALID'` is applied. Since `'MSG123' == 'VALID'` evaluates to `false`, a `SemanticViolation` is generated.
- **Result:** `schemaStatus = PASS`, `modelStatus = SUCCESS`, `semanticStatus = FAIL`. 
- **Proof:** This definitively proves the architecture can isolate and enforce business logic beyond static XML schemas.

---

## PART 17 — NULL / COLLECTION / TYPE FINDINGS

- **Nulls:** `?.` works natively and prevents evaluation crashes on missing optional blocks.
- **Collections:** Supported natively via SpEL (`.?[]`).
- **Dates (XMLGregorianCalendar):** **INCOMPLETE**. Prowide models represent dates as `XMLGregorianCalendar`. SpEL's default comparison operators (`<`, `>`) do not understand this type. A `TypeConverter` is required before date rules can be evaluated.

---

## PART 18 — CURRENT ISOParser SEMANTIC INTEGRATION

`ISOParser.parse(String xml, SemanticRuleEngine semanticEngine)` implements strict phased execution:
1. **Identification** -> (fails -> `UNIDENTIFIABLE_INPUT`)
2. **Schema Validation** -> (fails -> `schemaStatus = FAIL`, model & semantic `SKIPPED`)
3. **Model Parsing** -> (fails -> `modelStatus = PROWIDE_MODEL_UNAVAILABLE...`, semantic `SKIPPED`)
4. **Semantic Validation** ->
   - Technical crash / malformed SpEL -> `semanticStatus = TECHNICAL_ERROR`
   - Evaluation returns false -> `semanticStatus = FAIL`
   - 0 rules apply -> `semanticStatus = NOT_APPLICABLE`
   - All rules pass -> `semanticStatus = PASS`

---

## PART 19 — CURRENT TEST STATUS

**Command:** `./gradlew clean test`
**Result:** `BUILD SUCCESSFUL in 4m 48s`
**Count:** `199 actionable tasks executed` -> `:iso20022-core:test` 483 tests completed, 0 failures.

Key Scenarios covered:
- `ISOParserHardeningAuditTest`: Spy proves Prowide is bypassed on schema fail.
- `SemanticIntegrationTest`: Full lifecycle (XSD PASS -> Model SUCCESS -> Semantic PASS/FAIL/TECH_ERROR).
- `ISOParserBoundaryTest`: Missing namespaces, missing schemas, malformed inputs.
- `SemanticRuleLoaderTest`: Fail-fast JSON validation.

---

## PART 20 — CURRENT FILE PLACEMENT / PLUGIN ARCHITECTURE AUDIT

**CRITICAL:** Currently, all newly developed FSS code is located *inside* the Prowide `iso20022-core` module under the `com.prowidesoftware.swift.model.mx.validation.*` packages.

| Component | Current location | Prowide dependency | FSS-owned? | Candidate future location |
| --- | --- | --- | --- | --- |
| `ISOParser` | `iso20022-core/.../validation` | No (Adapter) | Yes | FSS Orchestrator Module |
| `ISOMessageIdentifier` | `iso20022-core/.../validation` | No | Yes | FSS Orchestrator Module |
| `SchemaRegistry` | `iso20022-core/.../validation` | No | Yes | FSS Orchestrator Module |
| `ISOValidator` | `iso20022-core/.../validation` | No | Yes | FSS Orchestrator Module |
| `ProwideAdapter` | `iso20022-core/.../validation` | Yes | Yes | FSS Prowide Plugin |
| `SemanticRuleEngine` | `.../validation/semantic` | Yes (AbstractMX) | Yes | FSS Semantic Module |
| `SemanticRuleLoader` | `.../validation/semantic` | No | Yes | FSS Semantic Module |
| `SpELRuleEvaluator` | `.../validation/semantic` | No | Yes | FSS Semantic Module |

**Recommendation:** Before extracting this out into a separate FSS repository/plugin, the FSS validation layer should first complete its features (TypeConverter, Builders) to avoid premature abstraction friction. When ready, the orchestration layer should sit *above* Prowide, treating Prowide as an external JAR dependency.

---

## PART 21 — SCHEMA REGISTRY TECHNICAL DEBT

`SchemaRegistry.locateFile()` currently looks up XSDs using filesystem paths:
`Paths.get(baseSchemasDir, category, identifier, identifier + ".xsd")`
- **Why it matters:** This fails when the library is packaged inside a JAR, WAR, or Docker container because `File` cannot read inside standard JAR archives.
- **Future Solution:** Migrate to `ClassLoader.getResourceAsStream()` for resolving schemas off the classpath. 
- **Tests:** Currently passing because Gradle executes tests in a local filesystem context where relative paths work.

---

## PART 22 — BUILDER STATUS

**The Builder requirement is currently UNDERSPECIFIED and NOT IMPLEMENTED.**
The project roadmap aims for `Parser + Validator + Builder`. While `Parser` and `Validator` are heavily structured, the `Builder` layer has no definition yet.
Future questions to resolve before starting work on the Builder:
- Will FSS offer fluent Java builders wrapping Prowide types?
- Is it for XML creation, JSON conversion, or FSS-internal routing object construction?
- Should it enforce schemas before serialization?

---

## PART 23 — WHAT IS COMPLETE

```text
PHASE 1 — Parser Orchestration
[COMPLETE]

PHASE 2 — XSD Validation
[COMPLETE]

PHASE 3 — Semantic Prototype
[COMPLETE]

PHASE 3 — Production Semantic Rule System
[PARTIAL - Engine complete, date-type conversion missing]

Schema Classpath Portability
[NOT COMPLETE]

Date/Time Type Conversion
[NOT COMPLETE]

Full Message Catalogue
[NOT COMPLETE]

Builder
[NOT STARTED]
```

---

## PART 24 — WHAT REMAINS

### Immediate Next Technical Tasks
1. **XMLGregorianCalendar TypeConverter:** Enable SpEL to process Prowide date comparisons.
2. **Date Semantic Comparison Proof:** Validate a date logic rule (e.g., `<`).
3. **SchemaRegistry Classpath Loading:** Portability fix.
4. **Multi-Message Proof:** Execute the semantic engine against `camt`, `pain`, and `seev` samples to definitively prove generic FSS capability.
5. **Rule Version Applicability:** Enforce `versions` array matching in `SemanticRuleEngine`.

### Later Work
- Full semantic rule catalogue (MDR/MUG mappings).
- Full target schema message catalogue ingestion.
- Builder API design.
- Result aggregation (JSON standardizing of `ISOParserResult`).
- Extraction of FSS code out of `iso20022-core` module.

---

## PART 25 — NEXT SESSION INSTRUCTIONS

**START HERE NEXT SESSION:**
1. Read `PROJECT_CONTEXT.md`.
2. Inspect current repository state.
3. Verify the relevant source files inside `iso20022-core/src/main/java/com/prowidesoftware/swift/model/mx/validation`.
4. Continue from the documented state (Phase 3).
5. **Do not modify Prowide internals.**
6. **Do not redesign Phase 1 or 2 without explicit evidence.**
7. Preserve the `Parser → Validator → Semantic Validator → Builder` architecture.
8. Keep FSS-owned code conceptually separable from Prowide.
9. Implement only the next explicitly approved task: **XMLGregorianCalendar TypeConverter for SpEL.**

---

## PART 26 — EXACT CURRENT STATE

```text
PROJECT:
FSS ISO 20022 Parser / Validator / Builder

CURRENT PHASE:
Phase 3 — Semantic Validation

COMPLETED:
- ISOParser Orchestration (Phase 1)
- SAX XSD Validation (Phase 2)
- ProwideAdapter Isolation Boundary
- SpEL SemanticRuleEngine Integration (Phase 3 Core)
- JSON SemanticRuleLoader

CURRENTLY WORKING:
SpEL Date comparison support

KNOWN TECHNICAL DEBT:
- SchemaRegistry uses filesystem (Paths.get) instead of Classpath loading.
- ISOParserResult uses AbstractMX directly (minor FSS leakage).
- Rule version array defined but not enforced in engine filtering.

NOT IMPLEMENTED:
- XMLGregorianCalendar TypeConverter for SpEL.
- Builder / Output layer.
- Full 35-message FSS schema/rule catalogue.

NEXT APPROVED INVESTIGATION:
XMLGregorianCalendar integration with SpEL contexts.

NEXT IMPLEMENTATION:
Implement FSS TypeConverter for SpEL date math.

DO NOT TOUCH:
Phase 1 and Phase 2 architectures (ISOValidator, SchemaRegistry, ISOMessageIdentifier).

IMPORTANT FILES:
iso20022-core/src/main/java/com/prowidesoftware/swift/model/mx/validation/ISOParser.java
iso20022-core/src/main/java/com/prowidesoftware/swift/model/mx/validation/semantic/SemanticRuleEngine.java

LAST VERIFIED TEST RESULT:
BUILD SUCCESSFUL (483 tests passed in :iso20022-core)

LAST UPDATED:
2026-10-05
```
