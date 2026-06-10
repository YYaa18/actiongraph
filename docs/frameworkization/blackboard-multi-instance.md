# Blackboard Multi-Instance Support

`Blackboard` now supports multiple objects of the same Java type through `BlackboardKey<T>`.

## Default Value Compatibility

The original API still works:

```java
blackboard.put(new CustomerId("C001"));
blackboard.get(CustomerId.class);
```

This stores and reads the default key:

```java
BlackboardKey.of(CustomerId.class) // CustomerId#default
```

## Keyed Values

Use explicit keys when a run needs more than one object of the same type:

```java
BlackboardKey<Contract> current = BlackboardKey.of(Contract.class, "current");
BlackboardKey<Contract> renewal = BlackboardKey.of(Contract.class, "renewal");

blackboard.put(current, currentContract);
blackboard.put(renewal, renewalContract);

blackboard.get(current);
blackboard.getAll(Contract.class);
```

`get(Class<T>)` remains convenient for single-object flows:

- returns the default value when present
- returns the only value of that type when exactly one keyed value exists
- throws when multiple non-default values exist, because the caller must choose a key

## Snapshots

`snapshotObjects()` remains as a legacy single-object view.

New code should use:

```java
Map<BlackboardKey<?>, Object> entries = blackboard.snapshotEntries();
```

Trace previews and JDBC persistence now use `snapshotEntries()`, so multiple same-type objects are audited and restored.

## Annotation SPI

Annotated actions can read/write keyed Blackboard values with `@BlackboardValue`.

```java
@ActionGraphAction(id = "contract.renewal.prepare")
@BlackboardValue("renewal")
Contract prepare(@BlackboardValue("current") Contract current) {
    return renewalFrom(current);
}
```

Without `@BlackboardValue`, annotation behavior remains the same as before and uses the default lookup.

## Persistence

`actiongraph-persistence-jdbc` serializes each Blackboard entry as:

- concrete class name
- Blackboard key id
- JSON value

Older snapshots without a key id are read back as `default`.

For production resume paths, configure `JdbcSuspendedRunRepository` with a `BlackboardTypeRegistry` so only expected domain object classes/packages can be restored from suspended-run JSON.
