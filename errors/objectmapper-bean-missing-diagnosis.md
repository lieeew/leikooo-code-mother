# ObjectMapper Bean Missing — Diagnosis Report

## Error Phenomenon
- Error message / stack trace: `AppVersionServiceImpl` constructor requires `ObjectMapper`, but no bean found.
- Reproduction conditions: After upgrading to Spring Boot 4.0.3, app fails at startup.

## Code Analysis
- Involved files and critical line numbers:
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\service\impl\AppVersionServiceImpl.java:56` — field `private final ObjectMapper objectMapper;`
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\service\impl\AppVersionServiceImpl.java:61` — constructor requires `ObjectMapper`.
  - `E:\Job-Coding\code-mother\pom.xml` — no `spring-boot-starter-json` or `jackson-databind` dependency declared.

## Root Cause Identification
**Root Cause:** The project has no Jackson dependency or `ObjectMapper` bean configuration, so Spring cannot provide `ObjectMapper` for constructor injection.
**Evidence:** `pom.xml` contains no Jackson starter, and there is no `@Bean ObjectMapper` in config.

## Eliminated Hypotheses
1. Bean excluded by conditional config — elimination reason: no `@Conditional` or profile gates found for `ObjectMapper`.
2. Conflicting Jackson versions — elimination reason: no Jackson dependency present at all in pom.

## Fix Plan
- Files to modify: `E:\Job-Coding\code-mother\pom.xml` OR add a config class.
- Modification details: add `spring-boot-starter-json` dependency (preferred) or define a `@Bean ObjectMapper` in a configuration class.
