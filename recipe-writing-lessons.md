# Recipe Writing Lessons

This document captures key lessons learned while developing OpenRewrite recipes.

## TypeUtils Best Practices

### Use TypeUtils methods instead of instanceof checks

When working with JavaType objects, prefer using TypeUtils helper methods over direct instanceof checks:

**Good:**
```java
JavaType.Array arrayType = TypeUtils.asArray(targetType);
if (arrayType != null) {
    JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
    if (fqType != null) {
        // Use fqType
    }
}
```

**Avoid:**
```java
if (targetType instanceof JavaType.Array) {
    JavaType.Array arrayType = (JavaType.Array) targetType;
    if (componentType instanceof JavaType.FullyQualified) {
        JavaType.FullyQualified fqType = (JavaType.FullyQualified) componentType;
        // Use fqType
    }
}
```

### Benefits of TypeUtils
- Safer type checking and casting
- More idiomatic OpenRewrite code
- Handles null cases gracefully
- Consistent with the framework's patterns

## JavaType Hierarchy

### JavaType.Class extends JavaType.FullyQualified
Since `JavaType.Class` is a subtype of `JavaType.FullyQualified`, you only need to check for the parent type:

**Simplified:**
```java
JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(componentType);
if (fqType != null) {
    // This handles both JavaType.Class and JavaType.FullyQualified
}
```

## MethodMatcher for Reliable Method Matching

When checking for specific method calls, use `MethodMatcher` instead of manual checks:

**Good:**
```java
private static final MethodMatcher TO_ARRAY = new MethodMatcher("java.util.Collection toArray()", true);

if (TO_ARRAY.matches(methodInvocation)) {
    // Handle toArray() call
}
```

**Avoid:**
```java
if (methodInvocation.getName().getSimpleName().equals("toArray") &&
    methodInvocation.getArguments().size() == 0 &&
    methodInvocation.getMethodType() != null &&
    TypeUtils.isAssignableTo("java.util.Collection", methodInvocation.getMethodType().getDeclaringType())) {
    // Handle toArray() call
}
```

## JavaTemplate Usage

### Import handling
Always declare imports when the template introduces types:
```java
JavaTemplate template = JavaTemplate.builder("#{any()}.toArray(new #{}[0])")
        .imports(fqn)  // Important: declare the import
        .build();
```

### Don't forget maybeAddImport()
After applying a template that uses a type, add the import to the compilation unit:
```java
Expression result = template.apply(...);
maybeAddImport(fqn);  // Add the import to the source file
```

## Testing Best Practices

### Type Validation in Tests
For tests involving custom types that may have incomplete type information, consider disabling type validation.
But only use this as an absolute last resort. Prefer to specify types that exist.

```java
@Test
void withCustomType() {
    rewriteRun(
      spec -> spec.typeValidationOptions(TypeValidation.none()),
      java(
        // test code
      )
    );
}
```

### Suppressing IDE Warnings in Tests
When testing code that contains intentional issues (that your recipe will fix), suppress IDE warnings:

```java
// For a single test method
@SuppressWarnings("RedundantCast")
@Test
void testRedundantCast() {
    rewriteRun(
      java(
        """
          String[] array = (String[]) list.toArray(); // IntelliJ warns about redundant cast
          """
      )
    );
}

// For multiple tests with the same warning, move to class level
@SuppressWarnings({"ConstantConditions", "RedundantCast"})
class MyRecipeTest implements RewriteTest {
    // All test methods inherit these suppressions
}
```

Common suppressions for recipe tests:
- `"RedundantCast"` - for recipes fixing unnecessary casts
- `"ConstantConditions"` - for null checks that will be removed
- `"unused"` - for unused variables/methods that will be removed
- `"unchecked"` - for unchecked operations that will be fixed

### Test Coverage
Ensure comprehensive test coverage including:
- Basic cases
- Edge cases (custom types, fully qualified types)
- Cases where the recipe should NOT make changes
- Import handling scenarios
- Formatting preservation

### IDE Support with Language Comments
Add language comments before string templates containing code to enable IDE syntax highlighting.

When using `java("before", "after")` with no customization, place the comment before `java`:
```java
@Test
void testMethod() {
    rewriteRun(
      //language=java
      java(
        """
          public class Before {
              // Both strings get syntax highlighting
          }
          """,
        """
          public class After {
              // Both strings get syntax highlighting
          }
          """
      )
    );
}
```

When there's customization or multiple `java()` calls, place comments on individual strings:
```java
@Test
void testWithCustomization() {
    rewriteRun(
      spec -> spec.typeValidationOptions(TypeValidation.none()),
      //language=java
      java(
        """
          public class Test {
              // Java code with syntax highlighting
          }
          """
      )
    );
}
```

**Important**: Do NOT add `//language=java` to JavaTemplate strings that contain parameters like `#{any()}` or `#{}`, as these are not valid Java syntax and will cause IDE errors:
```java
// DON'T DO THIS - parameters make it invalid Java
JavaTemplate template = JavaTemplate.builder(
    //language=java  // ❌ Wrong!
    "#{any()}.toArray(new #{}[0])"
).build();

// Only use language comments for valid Java code
JavaTemplate template = JavaTemplate.builder(
    //language=java  // ✅ OK - valid Java
    "System.out.println(\"Hello\")"
).build();
```

## Recipe Metadata

### Always include RSPEC tags
When implementing SonarQube rules, include the RSPEC identifier:
```java
@Override
public Set<String> getTags() {
    return Collections.singleton("RSPEC-S3020");
}
```

### Accurate time estimates
Provide realistic time estimates for manual fixes. When implementing SonarQube rules, use the same time estimate that is on the SonarQube definition.
```java
@Override
public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(2);
}
```

## Visitor Patterns

### JavaVisitor vs JavaIsoVisitor

Use `JavaIsoVisitor` when:
- You're returning the same type of LST element that you're visiting
- You want to avoid casting the visited element
- Most common for simple transformations

Use `JavaVisitor` when:
- You need to return a different type of LST element than what you're visiting
- Example: Visiting a `J.Parentheses` but returning an `Expression` after unwrapping/replacing it
- This is necessary when the transformation fundamentally changes the tree structure

```java
// JavaVisitor example - returning different type
@Override
public J visitParentheses(J.Parentheses parentheses, ExecutionContext ctx) {
    // ... some logic ...
    return someExpression;  // Not a J.Parentheses
}

// JavaIsoVisitor example - returning same type
@Override
public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
    J.TypeCast tc = super.visitTypeCast(typeCast, ctx);
    // ... some logic ...
    return tc;  // Still a J.TypeCast
}
```

### Handle parentheses explicitly
When dealing with expressions that might be parenthesized, consider visiting parentheses nodes:
```java
@Override
public J visitParentheses(J.Parentheses parentheses, ExecutionContext ctx) {
    // Handle parenthesized expressions
}
```

### Preserve formatting
When replacing expressions, be mindful of preserving prefixes and formatting:
```java
return visitedParentheses.withTree(result);  // Preserves parentheses structure
```

## YAML Configuration

### Add recipes to appropriate recipe collections
Don't forget to add new recipes to relevant YAML files.

## Making a new recipe visible to the Moderne CLI

`mod run --recipe <name>` resolves against the marketplace built from the recipe JAR's
`META-INF/rewrite/recipes.csv`, not from a classpath scan. A recipe that `Environment.builder().scanRuntimeClasspath(..)`
finds will still fail with `Unable to find recipe` until `./gradlew recipeCsvGenerate` adds it to that file
(run it on its own; it conflicts with `publishToMavenLocal` in the same invocation). Publish under a version
of your own (`-Prelease.version=2.99.1-SNAPSHOT`) so the CLI resolves the local jar rather than the CI
snapshot of the same coordinates, then `mod config recipes jar install org.openrewrite.recipe:rewrite-static-analysis:<that version>`.

`./gradlew build` enforces this: `recipeCsvValidateCompleteness` fails with "Recipe exists in environment but is
not listed in CSV" for anything missing, so a new recipe of any kind — including one declared only in YAML —
breaks the build until `recipeCsvGenerate` runs. Editing a recipe's display name or description changes its CSV
row too, so regenerate after rewording as well.

## Rewriting XML (PMD rulesets)

- **`rewrite-xml` is not on this project's classpath by default.** Add it to the `provided` configuration
  alongside the language modules, which keeps it off downstream consumers' compile classpaths while making it
  available to `compileJava` and to tests. Without it, `org.openrewrite.xml.Assertions.xml` does not resolve.
- **Guard on the root tag, not a path glob.** PMD rulesets are not named consistently (`ruleset.xml`,
  `pmd-rules.xml`, ...), and elements as generic as `<rule>` and `<exclude>` appear in unrelated XML. Returning
  `document` unchanged from `visitDocument` when the root tag is not `<ruleset>` scopes a recipe far more
  reliably than `FindSourceFiles`.
- **`RemoveContentVisitor` is the way to delete a tag**, via
  `doAfterVisit(new RemoveContentVisitor<>(tag, removeEmptyAncestors, removePrecedingComment))`. It fixes up the
  following sibling's prefix so indentation survives, and takes the preceding comment with it. Pass
  `removeEmptyAncestors = false` unless a container really is meaningless once empty — an emptied
  `<ruleset name="..">` should still be there.
- **`AddToTagVisitor` appends a child and infers its formatting** from the parent's existing children, so
  `doAfterVisit(new AddToTagVisitor<>(root, Xml.Tag.build("<rule ref=\"..\"/>")))` lands a new `<rule>` at the
  end of a `<ruleset>` correctly indented, even when the ruleset had no `<rule>` children yet.
- **A whole-file reference is a different thing from a rule reference.** `<rule ref="category/java/errorprone.xml">`
  pulls in every rule in that file, so a recipe that adds a rule has to check for the file-level `ref` too:
  the rule may already be active, or held out by a single `<exclude>` that is what actually needs removing.
- **Reject ambiguous options with `validate()`** rather than silently no-op'ing. Removing or replacing a rule can
  work off a bare rule name, but adding one cannot — there is no ruleset file to resolve it against — so
  `Validated.test("rule", .., r -> r.indexOf('/') != -1)` surfaces the mistake at recipe instantiation.
- **A child tag's cursor parent is the parent `Xml.Tag` directly**, with no padding in between, so
  `getCursor().getParentTreeCursor().getValue()` reads the enclosing element. That matters for `<exclude>`,
  whose meaning depends on the `ref` of the `<rule>` it sits in.
- **`Xml.Attribute` values nest one level:** `attribute.withValue(attribute.getValue().withValue(newValue))`.
  `getValueAsString()` reads it back without the quotes.
