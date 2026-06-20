# Coding Standards & Conventions

## Java 21 Features to Use

### Records for Immutable Data
```java
// Use records for stat definitions, configurations, and event data
public record UnitStats(
    int typeId,
    String name,
    Faction faction,
    int maxHp,
    int armor,
    int moveSpeed,
    int attackDamage,
    int attackRange,
    int attackCooldown,
    int cost,
    int buildTime,
    int populationCost,
    boolean isInfantry,
    boolean isLargeUnit
) {}
```

### Sealed Classes for Command Pattern
```java
public sealed interface Command permits
    MoveCommand, AttackCommand, BuildCommand, ProduceCommand,
    ResearchCommand, GarrisonCommand, UngarrisonCommand {
    long tick();
    int playerId();
}
```

### Pattern Matching
```java
// Use pattern matching in switch expressions
String damageType = switch (projectile.type()) {
    case BULLET -> "kinetic";
    case ROCKET -> "explosive";
    case ARTILLERY -> "splash";
    case FLAME -> "thermal";
};
```

### Virtual Threads (for server)
```java
// Use virtual threads for concurrent connections in Spring Boot
@Configuration
public class ThreadConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadCustomizer() {
        return handler -> handler.setExecutor(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
```

## Code Style

### Formatting
- 4-space indentation (no tabs)
- Max line length: 120 characters
- Opening braces on same line
- One class per file
- Package declaration, imports, then class

### Naming
- Classes: PascalCase (`CombatSystem`)
- Methods: camelCase (`calculateDamage`)
- Constants: UPPER_SNAKE_CASE (`MAX_UNITS`)
- Fields: camelCase, private with getters (`private int maxHp`)
- Boolean getters: `is*` or `has*` (`isAlive()`, `hasPower()`)

### Comments
- Javadoc on all public classes and methods
- `// ASSUMPTION:` prefix for any assumption not in spec
- `// REF: section-X.Y` for cross-referencing RE documentation
- No `// TODO` — track in ProjectProgress.md instead

### Error Handling
- Use checked exceptions for recoverable errors
- Use unchecked exceptions for programming errors
- Never swallow exceptions silently
- Log all errors with context (SLF4J)
- Validate all external inputs (JSON data, network packets)

## Testing Standards

### Test Naming
```java
@Test
void shouldCalculateDamageWithArmorReduction() { ... }

@Test
void shouldRejectInvalidBuildingPlacement() { ... }

@Test
void shouldSyncGameStateAcrossTwoClients() { ... }
```

### Test Structure (Given-When-Then)
```java
@Test
void shouldApplyArmorReductionToDamage() {
    // Given
    var attacker = createUnit(ZEUS, CONFEDERATION);
    var target = createUnit(INFANTRY, RESISTANCE);
    int baseDamage = DamageCalculator.getBaseDamage(attacker, target);

    // When
    int actualDamage = DamageCalculator.calculate(attacker, target);

    // Then
    int expectedDamage = Math.max(Math.min(
        baseDamage * (10 - target.armor()) / 10,
        baseDamage - target.armor()), 1);
    assertEquals(expectedDamage, actualDamage);
}
```

### Coverage Requirements
- Core systems (combat, economy, pathfinding, AI): 90%+
- Network (lockstep, serialization): 85%+
- UI/Rendering: 60%+ (visual testing is manual)
- Overall: 80%+

## Gradle Conventions

### Dependency Management
```kotlin
// Use version catalog in gradle/libs.versions.toml
[versions]
java = "21"
fxgl = "21"
spring-boot = "3.3.0"
junit = "5.10.2"

[libraries]
fxgl-core = { module = "com.github.almasb:fxgl-core", version.ref = "fxgl" }
```

### Module build.gradle.kts Template
```kotlin
plugins {
    id("aow2.java-conventions")
}

dependencies {
    implementation(project(":aow2-common"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
```

## Git Commit Convention

### Format
```
type(scope): description

[optional body]

[optional footer]
```

### Types
- `feat`: New feature
- `fix`: Bug fix
- `test`: Adding or fixing tests
- `docs`: Documentation changes
- `refactor`: Code refactoring (no behavior change)
- `perf`: Performance improvement
- `build`: Build system changes
- `ci`: CI configuration
- `chore`: Other changes

### Scopes
- `core`: Core game engine
- `combat`: Combat system
- `economy`: Economy and buildings
- `ai`: AI system
- `path`: Pathfinding
- `net`: Networking
- `ui`: UI and rendering
- `map`: Map system and editor
- `mod`: Modding system
- `replay`: Replay system
- `campaign`: Campaign missions
- `server`: Spring Boot server
- `client`: FXGL client

### Examples
```
feat(combat): implement damage formula with armor reduction

Implemented the exact damage formula from combat_formulas.md:
damage = weaponDamage * (10 - targetArmour) / 10
Clamped between 1 and (weaponDamage - targetArmour)

REF: combat_formulas.md section "Damage Calculation for Projectiles"
```

```
fix(path): resolve unit deadlock on narrow passages

Units were getting stuck when two groups met on a narrow path.
Added stuck detection counter and re-routing logic matching
the original game's stuckCounter mechanism.

REF: unit_stats.md offset +1515 stuckCounter
```
