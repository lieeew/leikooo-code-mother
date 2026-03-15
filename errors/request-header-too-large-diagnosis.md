# Request Header Too Large — Diagnosis Report

## Error Phenomenon
- Error message / stack trace: `java.lang.IllegalArgumentException: Request header is too large`
- Reproduction conditions: When sending a request with an oversized request header (e.g., large JWT token or cookie).

## Code Analysis
- Involved files and critical line numbers:
  - `application.yml` / `application.properties` — server configuration
- Call chain diagram:
  - Client Request → Tomcat/Netty → Spring Boot → Filter/Interceptor → Controller

## Root Cause Identification
**Root Cause:** The HTTP request header size exceeds the default limit configured in the embedded server (Tomcat/Netty).
**Evidence:** Default max header size in Tomcat is 8KB. When JWT token or cookie is large, the limit is exceeded.

## Eliminated Hypotheses
1. Network proxy/load balancer header limit — elimination reason: error occurs at application level, not network level.
2. Browser cookie size limit — elimination reason: the error is thrown by the server, not the browser.

## Fix Plan
- Files to modify: `src/main/resources/application.yml`
- Modification details: Add the following configuration:
  ```yaml
  server:
    max-http-request-header-size: 10000000
  ```
- Alternative: For older Spring Boot versions, use `server.tomcat.max-http-header-size: 10000000`

## sources
resolve source: https://stackoverflow.com/questions/39720422/java-tomcat-request-header-is-too-large