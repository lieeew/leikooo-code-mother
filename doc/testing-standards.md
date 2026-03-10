# Testing Standards

## 1. Test Naming
- Test method names describe **behavior**, not implementation
- Format: `should_expectedBehavior_when_condition`
- Bad: `testCreateUser()` → Good: `should_returnUser_when_validInput()`

## 2. Test Structure
- Follow Given-When-Then / Arrange-Act-Assert structure
- Each test verifies exactly one behavior

## 3. Test Data
- Prohibit hardcoded magic values, use semantic constants or Builder
- Separate test data from production data

## 4. Mock Principles
- Mock only external dependencies (database, third-party APIs, message queues)
- Prohibit mocking methods of the class under test
- Prefer real objects, mock as last resort
