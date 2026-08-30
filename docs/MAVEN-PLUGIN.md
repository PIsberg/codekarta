# Maven Plugin

Generates code-karta diagrams as part of a Maven build, so a diagram is a build output rather
than something someone remembered to regenerate.

Requires Maven 3.9.11 or newer and a Java 17 runtime. The `elk` layout needs Java 21; on 17 it
falls back to `simple` and logs a warning, exactly as the CLI does.

## Zero configuration

```xml
<plugin>
    <groupId>se.deversity.codekarta</groupId>
    <artifactId>code-karta-maven-plugin</artifactId>
    <version>0.4.0</version>
    <executions>
        <execution>
            <goals><goal>generate</goal></goals>
        </execution>
    </executions>
</plugin>
```

That draws a class diagram of `src/main/java` into `target/code-karta/class-diagram.svg`.

The goal binds to `package` rather than `compile`, so it runs after the module has been shown to
build and stays out of the inner edit-compile loop.

Run it without binding it at all, and read the generated parameter reference:

```bash
mvn se.deversity.codekarta:code-karta-maven-plugin:0.4.0:generate
mvn se.deversity.codekarta:code-karta-maven-plugin:0.4.0:help -Ddetail=true
```

The short `mvn karta:generate` form needs the group registered in `~/.m2/settings.xml`, because
Maven only resolves a goal prefix against `org.apache.maven.plugins` and `org.codehaus.mojo`
otherwise. Declaring the plugin in the pom does not register the prefix:

```xml
<pluginGroups>
    <pluginGroup>se.deversity.codekarta</pluginGroup>
</pluginGroups>
```

## Parameters

Every parameter mirrors a CLI flag of the same name, so [`CLI.md`](CLI.md) is the reference for
what each one does to the diagram. This table covers what is specific to the plugin.

| Parameter | Property | Default | Notes |
|---|---|---|---|
| `input` | `karta.input` | `${project.build.sourceDirectory}` | A file, a directory, or a `module-info.java`. The shape of the path selects the diagram type. |
| `outputDirectory` | `karta.outputDirectory` | `${project.build.directory}/code-karta` | Created if missing. |
| `outputName` | `karta.outputName` | derived | A plain file name. One with separators or `..` is refused. |
| `format` | `karta.format` | `svg` | `svg` or `json`. |
| `layout` | `karta.layout` | `simple` | `simple` or `elk`. |
| `sequenceOnly` | `karta.sequenceOnly` | `false` | |
| `stateMachine` | `karta.stateMachine` | `false` | |
| `modulesOnly` | `karta.modulesOnly` | `false` | The one reason to run on a `pom` module. |
| `splitPackages` | `karta.splitPackages` | `false` | Mirrors the package tree under `outputDirectory`. |
| `excludes` | | empty | An `<excludes><exclude>*Test</exclude></excludes>` list, not the CLI's comma-separated string. |
| `maxDepth` | `karta.maxDepth` | unbounded | |
| `maxMembers` | `karta.maxMembers` | `6` | |
| `diagrams` | | empty | Several diagrams from one execution. See below. |
| `skip` | `karta.skip` | `false` | |
| `failOnEmpty` | `karta.failOnEmpty` | `false` | See "When nothing is drawn". |

## Several diagrams from one execution

Each `<diagram>` inherits every value it does not set from the goal-level parameters, which is
what keeps the single-diagram case configuration-free. Derived file names collide when several
diagrams share an output directory, so give each one an `outputName`.

```xml
<configuration>
    <outputDirectory>${project.basedir}/docs/diagrams</outputDirectory>
    <excludes>
        <exclude>*Test</exclude>
    </excludes>
    <diagrams>
        <diagram>
            <outputName>classes.svg</outputName>
            <layout>elk</layout>
        </diagram>
        <diagram>
            <outputName>graph.json</outputName>
            <format>json</format>
        </diagram>
        <diagram>
            <input>${project.basedir}/src/main/java/com/example/Order.java</input>
            <outputName>order-flow.svg</outputName>
            <sequenceOnly>true</sequenceOnly>
        </diagram>
    </diagrams>
</configuration>
```

Two rules govern the merge, and both come from what a Maven configuration bean can and cannot
express:

- **`excludes` lists are combined**, goal-level plus per-diagram, deduplicated in that order.
- **Booleans are OR-ed, not overridden.** The bean cannot tell an unset `boolean` from an
  explicit `false`, so a goal-level `<stateMachine>true</stateMachine>` applies to every diagram
  in the execution and a per-diagram `false` cannot cancel it. Set such flags per diagram, not at
  the goal level, when they should not apply to all of them.

## Committing diagrams

`outputDirectory` defaults under `target/` so that a build does not write into the source tree
unasked. A project that wants its diagrams reviewable in pull requests points it at
`docs/diagrams` instead. `KartaCli.run` is idempotent, so identical input regenerates
byte-identical output and a committed diagram changes only when the code does.

## Declaring it once in a parent pom

This is how plugins are actually adopted, so two ordinary cases are deliberately not failures:

- **A module with no `src/main/java`** logs at debug level and moves on.
- **A `pom`-packaging aggregator** is skipped with a message saying so, because it has no sources
  of its own. Set `modulesOnly` to draw the reactor there instead.

## When nothing is drawn

Parsers never throw. A parse failure logs a warning and returns an empty graph, which means a
source tree that failed to parse looks exactly like one with nothing to draw. The build says so
either way:

```
code-karta produced no diagram for <path>. Either there was nothing to draw, or parsing
failed: parsers log a warning and return an empty graph rather than throwing, so check the
build log.
```

Set `failOnEmpty` to `true` for a module whose diagram is a deliverable, and that message becomes
a build failure. Leave it off in a parent pom, where empty modules are normal.

## How this page is kept true

The configurations above are not illustrations. Each one is an integration test under
[`code-karta-maven-plugin/src/it/`](../code-karta-maven-plugin/src/it/), run by
`maven-invoker-plugin` during `mvn verify` as a real Maven build against a real pom, with a
`verify.groovy` asserting the files it should have produced. The zero-configuration project, the
three-diagram list, the skipped aggregator and the `failOnEmpty` failure each have one.

`PluginDescriptorTest` covers the other direction: it reads the generated plugin descriptor and
fails when the parameter table above and the descriptor disagree, so a renamed parameter cannot
leave this page quietly wrong.

Skip the integration tests with `-Dinvoker.skip=true` when iterating; CI does not.

## Related

- [`CLI.md`](CLI.md) for what each flag does to the diagram.
- [`DIAGRAM-MODES.md`](DIAGRAM-MODES.md) for which input path produces which diagram.
- [`LIBRARY.md`](LIBRARY.md) for driving the tiers from Java instead.
