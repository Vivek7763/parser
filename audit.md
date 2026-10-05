# Architecture Audit

1. **What is already correct?**
- `ISOMessageIdentifier` accurately isolates ISO identification logic (namespace, version, function).
- `ISOValidator` uses stream-based filtering to extract `<Document>` effectively for validation without full memory overhead.
- `ISOParser` stops model parsing gracefully when schema validation fails.
- `ISOParserResult` maintains clear separation of `SchemaValidationStatus` and `ModelParsingStatus`.

2. **What is duplicated?**
- None currently, but if we tried to distinguish Prowide's `null` return on `AbstractMX.parse`, we'd duplicate `MxId.camelized()` and `Class.forName` reflection paths.

3. **What is tightly coupled?**
- `ISOParser` directly uses `MxParseUtils.identifyMessage(xml)` and `MxId`. It is acceptable to use `MxParseUtils` as a utility, but passing `MxId` to `ISOMessageIdentifier` couples our internal generic model to Prowide's `MxId`.
- Prowide's parsing boundary isn't abstracted. `ISOParser` calls `AbstractMX.parse` directly.

4. **What prevents ISOParser from being a genuinely generic parser?**
- Nothing currently restricts it (previous message-specific logic `if pacs.002` was removed), BUT it hasn't been extracted into a pure `ProwideAdapter`. 
- The schema resolution relies on `schemas/pacs/...`, assuming business area is always 4 chars.

5. **What assumptions are hardcoded?**
- `SchemaRegistry` assumes schemas are stored in `schemas/<category>/<namespace>/<namespace>.xsd` and hardcodes category extraction `identifier.substring(0, 4)`.
- It also assumes XSDs will always be provided on the filesystem (`schemaPath.toFile()`).

6. **What should be changed before expanding to more message types?**
- **Prowide APIs limitation:** `MxReadImpl.parse` swallows `ClassNotFoundException` and parsing exceptions into a generic `null` return. The `ISOParserResult` wrapper must merge `MODEL_NOT_AVAILABLE` and `PARSE_ERROR` into a single, honest status because we cannot distinguish them cleanly without hacking Prowide internals.
- **ProwideAdapter:** Isolate `MxParseUtils.identifyMessage()` and `AbstractMX.parse()` behind an adapter so `ISOParser` orchestration is purely generic.
- **ISOMessageIdentifier:** Must parse URNs itself instead of relying on `MxId` properties.
