# Assertion Migration Case Study: AssertJ to Reality Check

> Real-world migration of **sqlsage4j** (94 unit tests across 29 test files) from AssertJ to Reality Check.

---

## Migration Summary

| Metric | Value |
|---|---|
| Total test files migrated | 29 |
| Total test methods | 94 |
| Drop-in replacements (no change needed) | ~70% |
| Assertions requiring adaptation | ~30% |
| Compilation errors after initial swap | 19 (all resolved) |
| Lines of test code reduced | Net neutral (slightly cleaner in JSON tests) |

---

## Assertion Migration: Before (AssertJ) vs Now (Reality Check)

### 1. Enum Equality

AssertJ uses the generic `assertThat()` for enums. Reality Check provides a dedicated `assertThatEnum()` entry point for type safety.

```java
// Before (AssertJ)
assertThat(sys.role()).isEqualTo(ChatRole.SYSTEM);
assertThat(sqlSage4j.rerankingAlgorithm()).isEqualTo(RerankingAlgorithmEnum.GPT_RANKING);

// After (Reality Check)
assertThatEnum(sys.role()).isEqualTo(ChatRole.SYSTEM);
assertThatEnum(sqlSage4j.rerankingAlgorithm()).isEqualTo(RerankingAlgorithmEnum.GPT_RANKING);
```

**Files affected:** `ChatMessageTest`, `SqlSage4jTest`, `SqlPromptBuilderTest`

---

### 2. Object Not-Null

AssertJ uses the generic `assertThat()` for any object. Reality Check uses `assertThatObject()` which returns `ObjectCheck<T>` with `.isNotNull()` inherited from the `Check` interface.

```java
// Before (AssertJ)
assertThat(sqlSage4j).isNotNull();
assertThat(cached).isNotNull();
assertThat(cached.df()).isNotNull();

// After (Reality Check)
assertThatObject(sqlSage4j).isNotNull();
assertThatObject(cached).isNotNull();
assertThatObject(cached.df()).isNotNull();
```

**Files affected:** `SqlSage4jEndToEndTest`, `MultiTurnConversationIT`

---

### 3. Custom Object Equality

AssertJ delegates to `.equals()` via its generic `assertThat()`. Reality Check uses `assertThatObject()` with `.isEqualTo()` — a default method on the `Check` interface.

```java
// Before (AssertJ)
assertThat(a).isEqualTo(b);

// After (Reality Check)
assertThatObject(a).isEqualTo(b);
```

**Files affected:** `ChatMessageTest`

---

### 4. Custom Object Inequality

```java
// Before (AssertJ)
assertThat(a).isNotEqualTo(b);

// After (Reality Check)
assertThatObject(a).isNotEqualTo(b);
```

**Files affected:** `ChatMessageTest`

---

### 5. instanceof Check

AssertJ uses `.isInstanceOf()` on its generic object assertion. Reality Check uses `assertThatObject()` with `.isInstanceOf()` — a default method on the `Check` interface.

```java
// Before (AssertJ)
assertThat(client).isInstanceOf(OpenAIClient.class);

// After (Reality Check)
assertThatObject(client).isInstanceOf(OpenAIClient.class);
```

**Files affected:** `LLMClientFactoryTest` (5 assertions)

---

### 6. Array Length

AssertJ has `.hasSize()` for arrays. Reality Check uses the array's `.length` property directly.

```java
// Before (AssertJ)
assertThat(embedding).hasSize(3);

// After (Reality Check)
assertThat(embedding.length).isEqualTo(3);
```

**Files affected:** `OllamaEmbeddingsProviderTest`

---

### 7. Float/Double Closeness

AssertJ requires a `within()` wrapper object for tolerance. Reality Check takes the tolerance as a direct parameter.

```java
// Before (AssertJ)
assertThat(embedding[0]).isCloseTo(0.1f, within(0.001f));
assertThat(embedding[1]).isCloseTo(0.2f, within(0.001f));

// After (Reality Check)
assertThat((double) embedding[0]).isCloseTo(0.1, 0.001);
assertThat((double) embedding[1]).isCloseTo(0.2, 0.001);
```

**Files affected:** `OllamaEmbeddingsProviderTest`, `VectorMathTest`

---

### 8. Object Cast for Primitive Equality

AssertJ auto-boxes and compares `Object` values. Reality Check's typed `assertThat()` overloads require explicit casting from `Object`.

```java
// Before (AssertJ)
assertThat(df.rows().get(0).get(0)).isEqualTo(1);
assertThat(df.rows().get(0).get(0)).isEqualTo("Alice");

// After (Reality Check)
assertThat((int) head.rows().get(0).get(0)).isEqualTo(1);
assertThat((String) df.rows().get(0).get(0)).isEqualTo("Alice");
```

**Files affected:** `DataFrameTest`, `SQLiteConnectorTest`, `MySQLConnectorTest`, `JdbcDatabaseConnectorTest`, `DuckDBConnectorTest`

---

### 9. Double from Object

```java
// Before (AssertJ)
assertThat(df.rows().get(0).get(0)).isEqualTo(247500.00);

// After (Reality Check)
assertThat((double) df.rows().get(0).get(0)).isEqualTo(247500.00);
```

**Files affected:** `SaasMetricsDashboardIT`

---

### 10. Long Literal Matching

When the method returns `long`, AssertJ auto-widens `int` literals. Reality Check requires explicit `long` literals.

```java
// Before (AssertJ)
assertThat(result.successCountA()).isEqualTo(3);

// After (Reality Check)
assertThat(result.successCountA()).isEqualTo(3L);
```

**Files affected:** `SqlSage4jEndToEndTest`, `ABTestWithLLMDiffIT`

---

### 11. Collection allSatisfy → allMatch

AssertJ's `allSatisfy` runs a `Consumer` with nested assertions. Reality Check uses `allMatch` with a `Predicate` and a description.

```java
// Before (AssertJ)
assertThat(suggestions).allSatisfy(q -> assertThat(q).isNotBlank());

// After (Reality Check)
assertThat(suggestions).allMatch(q -> !q.isBlank(), "is not blank");
```

**Files affected:** `DataTeamOnboardingIT`

---

### 12. Collection anyMatch with Description

Reality Check's `anyMatch` requires a description parameter for failure messages.

```java
// Before (AssertJ)
assertThat(concepts).anyMatch(c -> c.contains("revenue"));

// After (Reality Check)
assertThat(concepts).anyMatch(c -> c.contains("revenue"), "contains revenue");
```

**Files affected:** `SaasMetricsDashboardIT`

---

### 13. Descriptive Alias (.as())

AssertJ provides `.as("description")` on any assertion. Reality Check embeds descriptions in predicate-based methods instead.

```java
// Before (AssertJ)
assertThat(response.sql()).as("generated SQL").isNotEmpty();
assertThat(response.df()).as("query result").isNotNull();

// After (Reality Check)
assertThat(response.sql()).isNotEmpty();
assertThatObject(response.df()).isNotNull();
```

**Files affected:** `DataTeamOnboardingIT`, `EcommerceAnalyticsIT`, `MultiTurnConversationIT`

---

### 14. JSON Field Assertions (with realitycheck-json)

Manual Gson parsing replaced with fluent `assertThatJson()` from the `realitycheck-json` module.

```java
// Before (AssertJ + Gson)
JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
assertThat(req.get("model").getAsString()).isEqualTo("llama3");
assertThat(req.get("stream").getAsBoolean()).isFalse();
assertThat(req.getAsJsonObject("options").get("temperature").getAsDouble()).isEqualTo(0.1);

// After (Reality Check JSON)
assertThatJson(requestBody)
    .fieldEquals("model", "llama3")
    .fieldEquals("stream", false)
    .hasField("options.temperature");
```

**Files affected:** `OllamaClientTest`

---

### 15. JSON Array Check

```java
// Before (AssertJ + Gson)
JsonObject req = GSON.fromJson(requestBody, JsonObject.class);
assertThat(req.getAsJsonArray("messages").size()).isEqualTo(3);

// After (Reality Check JSON)
assertThatJson(requestBody).fieldIsArray("messages");
```

**Files affected:** `OllamaClientTest`

---

### 16. Collection Size via Method Call

When size comes from a method returning `int` on a non-collection type (e.g., `JsonArray.size()`), use the numeric `assertThat`.

```java
// Before (AssertJ)
assertThat(messages.size()).isGreaterThanOrEqualTo(2);

// After (Reality Check) — identical
assertThat(messages.size()).isGreaterThanOrEqualTo(2);
```

**Files affected:** `SqlPromptBuilderTest`

---

## Key Differences in Philosophy

| Aspect | AssertJ | Reality Check | Rationale |
|---|---|---|---|
| **Type dispatch** | Single generic `assertThat(Object)` resolves everything via overloading | Typed entry points: `assertThat(String)`, `assertThatEnum()`, `assertThatObject()`, `assertThatJson()` | No reflection — explicit dispatch, cleaner type inference at compile time |
| **Generic object checks** | `assertThat(obj).isNotNull()` / `.isInstanceOf()` via the same entry point | `assertThatObject(obj).isNotNull()` / `.isInstanceOf()` via dedicated entry point | Separates typed assertions (String, int, Collection) from generic Object assertions |
| **Tolerance syntax** | `.isCloseTo(3.14, within(0.01))` | `.isCloseTo(3.14, 0.01)` | Simpler API — no `Offset`/`Percentage` wrapper objects |
| **Assertion description** | `.as("description")` available on any assertion | Description embedded in predicate methods: `.allMatch(p, "desc")` | Fewer allocations, description tied to the actual check |
| **Collection predicates** | `.allSatisfy(Consumer)` — runs nested assertions inside lambda | `.allMatch(Predicate, "description")` — returns boolean | No assertion-in-assertion nesting; predicate is pure |
| **Custom extensions** | `AbstractAssert` subclass (~30 lines) | `record MyCheck(...) implements Check` (3 lines) | Java records eliminate boilerplate |
| **Soft assertions** | `SoftAssertions.assertSoftly(s -> ...)` | `assertAll(softly -> ...)` or `@WithSoftChecks` | Thread-safe by design |
| **JSON/XML/YAML** | Requires separate libraries (JsonUnit, XMLUnit) | First-class modules: `realitycheck-json`, `realitycheck-xml`, `realitycheck-yaml` | Unified API and failure messages |
| **Dependencies** | Zero (core only) | Zero (core only), Jackson for JSON module | Same lightweight philosophy |
| **Java version** | Java 8+ | Java 17+ | Leverages records, sealed classes, modern APIs |

---

## Migration Effort Summary

**Trivial (drop-in, no changes):**
- String assertions (`isEqualTo`, `contains`, `startsWith`, `isNotEmpty`, `isBlank`)
- Number comparisons (`isGreaterThan`, `isLessThan`, `isBetween`, `isPositive`)
- Collection assertions (`hasSize`, `contains`, `doesNotContain`, `containsExactly`)
- Map assertions (`containsKey`, `containsEntry`)
- Exception assertions (`assertThatThrownBy`, `isInstanceOf`, `hasMessageContaining`)

**Minor adaptation required:**
- Enum assertions → use `assertThatEnum()`
- Object null/type/equality checks → use `assertThatObject()`
- Float tolerance → remove `within()` wrapper, cast to `double`
- `Object` return types → add explicit casts to typed `assertThat()` overloads
- `allSatisfy` → `allMatch` with predicate + description
- `.as("desc")` → remove (or embed in predicate description)
- `int` vs `long` literals → match method return type exactly

**Net improvement (cleaner with Reality Check):**
- JSON assertions → `assertThatJson()` replaces manual Gson parsing
- Soft assertions → `@WithSoftChecks` parameter injection
- Custom checks → 3-line records vs 30-line classes
