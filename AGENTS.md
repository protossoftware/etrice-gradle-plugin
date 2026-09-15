# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

## What this project is

Gradle plugins that integrate the [eTrice](https://www.eclipse.org/etrice/) code generators
into Gradle builds. One Java project (`subprojects/de.protos.etrice.gradle`) publishes seven
plugin markers (`de.protos.etrice-base`, `de.protos.etrice-c`, `de.protos.etrice-java`,
`de.protos.model-library`, `de.protos.source-publish`, `de.protos.source-library`,
`de.protos.etunit-convert`). Versions are derived from git tags (`release-*`) via axion-release.

## Environment constraints

- The build currently uses **Gradle 7.6** (see `gradle/wrapper/gradle-wrapper.properties`).
  It **does not run on JDK 20+** (fails with "Unsupported class file major version").
  Use **JDK 17** (CI baseline) or another JDK 8–19. On machines with newer default JDKs,
  point `JAVA_HOME` at a JDK 17 installation.
- The plugins themselves compile with `options.release = 8` (minimum JVM of supported Gradle
  versions) and must stay free of APIs newer than the supported Gradle baseline unless the
  baseline is deliberately raised (see UPGRADE_PLAN.md).

## Common commands

```bash
./gradlew build            # full build incl. functional tests and plugin validation
./gradlew test             # functional tests only
./gradlew :subprojects:de.protos.etrice.gradle:test --tests "*.FunctionalTests"  # single class
./gradlew buildSite        # render AsciiDoc docs + javadoc to doc/build/site
./gradlew clean
```

Functional tests hit the network (Eclipse eTrice repositories) and download real generators;
expect the first `test` run to take a few minutes.

## Verification before submitting changes

1. `./gradlew build` must pass. Note: tests run TestKit builds with `--warning-mode=fail` —
   any deprecation warning introduced by your change fails the build.
2. `./gradlew buildSite` must pass when documentation or javadoc is touched.
3. Compilation uses `-Xlint:all -Werror`; there must be no compiler warnings.

## Code conventions

- Java sources live in `subprojects/de.protos.etrice.gradle/src/main/java/de/protos/etrice/gradle/`.
  Tests are Groovy (`src/test/groovy`) using JUnit 5 + Gradle TestKit.
- **Indentation: tabs** (also in Gradle build files). Match the existing style.
- Every public class and member has Javadoc (javadoc lint only has `missing` disabled).
- Gradle API usage follows lazy/immutable patterns: `NamedDomainObjectProvider`,
  `TaskProvider`, provider-based wiring; avoid eager task realization
  (note: `EtUnitConvertPlugin` intentionally realizes tasks inside the container factory).
- Configurations created by plugins must set `canBeConsumed`/`canBeResolved`/`visible`
  explicitly and stay `visible(false)` unless consumed by users.
- Dependency versions belong in `gradle/libs.versions.toml` (version catalog) or in
  `settings.gradle` `pluginManagement` for build plugins.
- The eTrice version used in tests, docs and the etunit converter default dependency must
  be kept consistent (`FunctionalTests.groovy`, `doc/build.gradle` attribute
  `version-etrice`, `EtUnitConvertPlugin.ETUNIT_CONVERTER_DEFAULT_DEPENDENCY`).
  The `compileOnly` dependency on `org.eclipse.etrice.generator.base` is the *minimum*
  supported eTrice version (currently 3.0.0) — do not raise it without a major version bump.

## Architecture pointers

- `ETriceBasePlugin` — the core: `modelSet` container of `ModelSource`, generator/modelpath
  configurations with attribute-based variant awareness (`LibraryElements` compatibility and
  disambiguation rules), `GenerateTask` per model source, `zipModel`, `eclipseModelpath`.
- `GenerateTask` → `WorkerExecutor.processIsolation` → `GeneratorWorker` (loads the generator
  Guice module by symbolic name, e.g. `etrice-c`, and runs `GeneratorApplication`).
  Environment variables are forwarded to the worker; `--add-opens java.base/java.lang=ALL-UNNAMED`
  keeps old eTrice/Xtext versions working on Java 9+. Preserve all of this.
- `ModelLibraryPlugin` / `SourceLibraryPlugin` / `SourcePublishPlugin` — download/extract/
  publish zip artifacts via configurations and `UnzipTask`; `AdhocComponentPlugin` provides
  the `adhoc` software component used for publishing.
- Configuration `modelpathZip` is intentionally `visible(false)` to avoid attaching `zipModel`
  to `assemble` (regression guarded by tests, see issue #4). Don't "simplify" this away.

## Release process

- Versions come from axion-release (`release-` tag prefix). Pushing a tag `release-X.Y.Z`
  triggers `.github/workflows/publish.yml` → Gradle Plugin Portal + GitHub Pages.
- Never commit changes that leave the working tree with snapshot version derivation issues;
  `scmVersion` is configured `localOnly` with uncommitted-changes checks disabled.
- Publishing requires the `GRADLE_PUBLISH_KEY`/`GRADLE_PUBLISH_SECRET` secrets.

## Planned upgrades

See [UPGRADE_PLAN.md](UPGRADE_PLAN.md) for the coordinated upgrade to Gradle 9 / JDK 25 and
current dependency versions. Keep that file updated as steps are completed.
