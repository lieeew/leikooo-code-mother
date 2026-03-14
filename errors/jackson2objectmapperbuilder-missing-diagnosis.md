# Jackson2ObjectMapperBuilder Missing — Diagnosis Report

## Error Phenomenon
- Error message / stack trace: `JsonConfig.jacksonObjectMapper` requires `Jackson2ObjectMapperBuilder`, but no such bean exists.
- Reproduction conditions: App startup after version upgrade; context refresh fails before web server starts.

## Code Analysis
- Involved files and critical line numbers:
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\config\JsonConfig.java:20` — `jacksonObjectMapper(Jackson2ObjectMapperBuilder builder)` hard-depends on a builder bean.
- Call chain diagram:
  - Spring context → `JsonConfig.jacksonObjectMapper` → requires `Jackson2ObjectMapperBuilder` → bean not found → startup fails

## Root Cause Identification
**Root Cause:** `JsonConfig` requires a `Jackson2ObjectMapperBuilder` bean, but no bean is registered in the current Boot 4 setup, so dependency resolution fails.
**Evidence:** No `@Bean Jackson2ObjectMapperBuilder` in project, and no `spring.autoconfigure.exclude`/`@EnableWebMvc` overrides found in code or config.

## Eliminated Hypotheses
1. Jackson auto-configuration explicitly excluded — elimination reason: no `spring.autoconfigure.exclude` or `@EnableWebMvc` found in repo configs.
2. Mapper scan or unrelated beans causing cascade failure — elimination reason: the failure occurs at `JsonConfig` bean creation before other components initialize.

## Fix Plan
- Files to modify: `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\config\JsonConfig.java`
- Modification details: remove the builder parameter and construct the `ObjectMapper` directly (or create a local `Jackson2ObjectMapperBuilder`), then register the Long→String module as before.
