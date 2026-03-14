# Long Serialized As Number — Diagnosis Report

## Error Phenomenon
- Error message / stack trace: none; API response still serializes Long as JSON number instead of String.
- Reproduction conditions: After removing `Jackson2ObjectMapperBuilder` dependency, `JsonConfig` builds `ObjectMapper` manually; frontend still receives numeric Long.

## Code Analysis
- Involved files and critical line numbers:
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\config\JsonConfig.java:18-30` — registers `ToStringSerializer` for `Long`/`long` on a custom `ObjectMapper` bean.
  - No `WebMvcConfigurer.extendMessageConverters` or custom `MappingJackson2HttpMessageConverter` wiring to enforce this mapper.
- Call chain diagram:
  - Spring MVC → `HttpMessageConverter` (Jackson) → `ObjectMapper` used for response → Long serialized as number

## Root Cause Identification
**Root Cause:** The ObjectMapper with Long→String module is not guaranteed to be the one used by Spring MVC message converters, so responses still serialize with the default mapper.
**Evidence:** There is no converter customization or explicit wiring to bind the custom mapper to `MappingJackson2HttpMessageConverter`.

## Eliminated Hypotheses
1. Missing serializer for primitive `long` — elimination reason: `Long.TYPE` is explicitly registered.
2. Explicit Fastjson/Gson converter overriding Jackson — elimination reason: no converter configuration found in codebase.

## Fix Plan
- Files to modify: `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\config\JsonConfig.java`
- Modification details: implement `WebMvcConfigurer` and set the custom `ObjectMapper` into `MappingJackson2HttpMessageConverter` in `extendMessageConverters` to ensure it is used for HTTP responses.
