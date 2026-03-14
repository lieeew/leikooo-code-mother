# MyBatis SqlSessionFactory Missing — Diagnosis Report

## Error Phenomenon
- Error message / stack trace: BeanCreationException for `observableRecordMapper` with "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required".
- Reproduction conditions: After upgrading Spring Boot to 4.0.3 and Spring AI to 2.0.0-M2, app startup fails during context refresh.

## Code Analysis
- Involved files and critical line numbers:
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\CodeMotherApplication.java:13` — `@MapperScan` enables mapper scanning.
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\mapper\ObservableRecordMapper.java:18` — Mapper interface is registered.
  - `E:\Job-Coding\code-mother\src\main\java\com\leikooo\codemother\config\MybatisPlusConfig.java:19` — Only pagination interceptor bean; no SqlSessionFactory bean defined.
  - `E:\Job-Coding\code-mother\pom.xml:77` — `mybatis-plus-spring-boot3-starter` is still used with Spring Boot 4.
- Call chain diagram:
  - `SpringApplication` → `@MapperScan` → `MapperFactoryBean` → `SqlSessionDaoSupport.checkDaoConfig` → `Assert.notNull(sqlSessionFactory/sqlSessionTemplate)`

## Root Cause Identification
**Root Cause:** `mybatis-plus-spring-boot3-starter` (3.5.15) is not compatible with Spring Boot 4.0.3, so MyBatis auto-configuration does not create `SqlSessionFactory`/`SqlSessionTemplate` beans.
**Evidence:** The mapper is scanned (bean creation reached `MapperFactoryBean`), but the required SQL session beans are missing. The pom still depends on the Boot 3 starter while the project is now on Boot 4.0.3.

## Eliminated Hypotheses
1. Datasource properties missing or invalid — elimination reason: `application-local.yml` provides full datasource properties; no datasource bean failure is shown in the stack trace.
2. Mapper scanning not enabled — elimination reason: `@MapperScan` exists and `ObservableRecordMapper` bean creation is attempted.

## Fix Plan
- Files to modify: `E:\Job-Coding\code-mother\pom.xml`
- Modification details: Replace `mybatis-plus-spring-boot3-starter` with a Spring Boot 4 compatible MyBatis Plus starter/version (or revert Spring Boot back to 3.5.x and keep the current starter), then re-run.
