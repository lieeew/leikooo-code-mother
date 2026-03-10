# Code Standards

## 1. Naming Conventions

### 1.1 Prohibit Non-Semantic Naming
- **Forbidden words**: `data`, `info`, `flag`, `process`, `handle`, `build`, `maintain`, `manage`, `modify`
- Names must describe **intent**, not implementation details
- Bad: `processChapter()` → Good: `startTranslation()`

### 1.2 Prohibit Technical Terms in Business Code
- Bad: `bookList`, `xxxMap`, `xxxSet` → Good: `books`, use plural for collections
- Bad: `redisBookStore` → Good: `cache` (interface-oriented naming, not implementation-oriented)
- Technical names (Redis/MQ/ES) in business code indicate missing business model abstraction

## 2. Eliminate Duplication (DRY)

### 2.1 Structural Duplication
- Extract identical function structures into higher-order functions
```java
// Extract try-catch-notify structure
private void executeTask(final Runnable runnable) {
  try {
    runnable.run();
  } catch (Throwable t) {
    this.notification.send(new SendFailure(t));
    throw t;
  }
}

@Task
public void sendBook() {
  executeTask(this.service::sendBook);
}
```

### 2.2 False Choice (if/else with only parameters different)
- If both branches are identical except parameters, eliminate the branch and extract the difference as a variable
```java
// Bad
if (user.isEditor()) {
  service.editChapter(chapterId, title, content, true);
} else {
  service.editChapter(chapterId, title, content, false);
}
// Good
boolean approved = user.isEditor();
service.editChapter(chapterId, title, content, approved);
```

## 3. Function Length

- Functions must not exceed **20 lines** (excluding blank lines), enforced by CheckStyle
```xml
<module name="MethodLength">
  <property name="tokens" value="METHOD_DEF"/>
  <property name="max" value="20"/>
  <property name="countEmpty" value="false"/>
</module>
```
- Splitting principle: **separate different business flows** + **separate different abstraction levels**
- Extract loops with more than 3 lines to independent functions

## 4. Class Design

### 4.1 Single Responsibility Decomposition
- Split classes with multiple roles into separate entities
```java
// Bad: User mixing Author/Editor fields
// Good: Split into User, Author, Editor classes
```

### 4.2 Field Grouping
- Encapsulate related fields into separate classes
```java
// Bad: email, phoneNumber scattered in User
// Good: Encapsulate as Contact class
```

## 5. Parameter Lists

### 5.1 Accumulate into Models — Parameter Encapsulation
- When more than 3 similar parameters exist, encapsulate as parameter class with behavior
```java
// Parameter class with construction behavior
public class NewBookParameters {
  // fields...
  public Book newBook() {
    return Book.builder.title(title)...build();
  }
}
```

### 5.2 Separate Static and Dynamic
- Parameters that change per call → keep as method parameters
- Unchanging dependencies → promote to class fields (via constructor injection)

### 5.3 Eliminate Flag Parameters
- Prohibit boolean/enum flag parameters controlling branching
- Split into semantically clear independent methods
```java
// Bad: editChapter(..., boolean approved)
// Good:
void editChapter(long chapterId, String title, String content);
void editChapterWithApproval(long chapterId, String title, String content);
```

## 6. Control Statements

### 6.1 Eliminate Nesting — Guard Clauses First
```java
// Bad: nested if statements
// Good: return immediately if preconditions not met
if (!epub.isValid()) return;
if (!registered) return;
this.sendEpub(epub);
```

### 6.2 Prohibit else
- Every branch returns directly, no else

### 6.3 Eliminate Repeated Switch — Use Polymorphism
- When same enum/type switches in multiple places, introduce polymorphic model
```java
interface UserLevel {
  double getBookPrice(Book book);
  double getEpubPrice(Epub epub);
}
// Each level has its own implementation class
```

## 7. Encapsulation

### 7.1 Prohibit Train Wrecks (Message Chains)
- Bad: `book.getAuthor().getName()`
- Good: `book.getAuthorName()` (hide delegation relationship)
- Follow Law of Demeter: only talk to direct friends

### 7.2 Eliminate Primitive Type Obsession
- Values with business meaning should be encapsulated as value objects, validation in constructor
```java
class Price {
  private long price;
  public Price(final double price) {
    if (price <= 0) throw new IllegalArgumentException("Price should be positive");
    this.price = price;
  }
}
```

## 8. Immutability

### 8.1 Prohibit Setters
- Lombok configuration to enforce:
```
lombok.setter.flagUsage = error
lombok.data.flagUsage = error
```
- Initialization via constructor or Builder only
- State changes return new objects, not modifying original
```java
public Book approve() {
  return new Book(..., ReviewStatus.APPROVED, ...);
}
```

### 8.2 Three Principles of Immutable Classes
1. All fields initialized only in constructor
2. All methods are pure functions
3. Return new object when change needed

## 9. Variable Declaration

### 9.1 Declaration and Assignment in One Step
- Bad: declare as null, assign later
- Good: extract function, assign in one step
```java
// Bad
EpubStatus status = null;
if (...) { status = ...; } else { status = ...; }
// Good
final EpubStatus status = toEpubStatus(response);
```

### 9.2 Use final Everywhere Possible
- Use final for all variables that don't need reassignment

### 9.3 One-Time Collection Initialization
```java
// Good (Java 9+)
List<Permission> permissions = List.of(Permission.BOOK_READ, Permission.BOOK_WRITE);
// Good (Guava)
ImmutableMap.of(LOCALE.ENGLISH, "EN", LOCALE.CHINESE, "CH");
```

### 9.4 Use try-with-resources for Exception Handling
```java
try (InputStream is = new FileInputStream(...)) { ... }
```

## 10. Modern Code Style

- Prefer Stream API over hand-written loops
```java
List<ChapterParameter> parameters = chapters.stream()
  .filter(Chapter::isApproved)
  .map(this::toChapterParameter)
  .collect(Collectors.toList());
```
- Lambda should ideally be single line
- Loops describe implementation details, Stream describes intent — prefer higher abstraction
