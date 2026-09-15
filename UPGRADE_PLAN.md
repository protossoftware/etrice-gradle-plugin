# Upgrade Plan — Gradle 9 / JDK 25 and dependency refresh

Status: **planned** (nothing executed yet). Work through the phases in order; each phase
leaves the build green (`./gradlew build buildSite`) and can ship independently.
Update this file as steps are completed.

## Current state (September 2026)

| Component | Current version | Latest stable | Notes |
| --- | --- | --- | --- |
| Gradle wrapper | **7.6** (Nov 2022) | **9.7.1** (Aug 2026, 9.8.0-RC1 pending) | Gradle 9 runs on JVM 17–26; Java 25 requires Gradle ≥ 9.1.0 |
| Build JDK (CI, local) | Temurin **17** | **25** (LTS; 26 non-LTS also supported by Gradle ≥ 9.4) | Verified: Gradle 7.6 fails on JDK 25 (`Unsupported class file major version 69`) |
| Java compile target | `options.release = 8` | keep 8 (or 17, see phase 4) | `release 8` still supported by javac 25 |
| `com.gradle.plugin-publish` | 1.3.1 | **2.2.1** | 2.x requires Gradle ≥ 7.4 ✓; adds feature-compatibility declaration |
| `pl.allegro.tech.build.axion-release` | 1.20.1 | **1.21.3** | 1.21.x tested against Gradle 7/8/9 on JDK 17/21/25 |
| `org.asciidoctor.jvm.convert` | 4.0.5 | **4.0.5** (5.0.0-alpha.1 exists) | Already latest stable; 4.0.5 contains the Gradle 9 fix (avoids `SelfResolvingDependency`) |
| JUnit Jupiter (`libs.versions.toml`) | 5.13.4 | **6.1.3** (5.x line: 5.14.4) | JUnit 6 requires Java 17+ at test runtime |
| eTrice (tests, docs, etunit default) | 5.4.0 | **5.9.0** | `compileOnly` minimum stays 3.0.0 (backwards compatibility) |
| `actions/checkout` | v4 | **v7** (v7.0.1) | |
| `actions/setup-java` | v4 | **v6** (v6.0.1) | |
| `peaceiris/actions-gh-pages` | v3 | **v4** (v4.1.0) | |

Compatibility research (official sources, checked 2026-09):

* Gradle compatibility matrix: https://docs.gradle.org/current/userguide/compatibility.html
  — Gradle 9 requires JVM 17+; Java 25 for running Gradle since 9.1.0; Java 26 since 9.4.0.
* plugin-publish 2.x: requires Gradle ≥ 7.4 (https://plugins.gradle.org/docs/publish-plugin);
  publishing without a feature-compatibility declaration is now deprecated.
* axion-release 1.21.3 release notes: CI matrix covers Gradle 7.x/8.x/9.x and JDK 17/21/25.
* asciidoctor-gradle 4.0.5 (Aug 2025): "Avoid SelfResolvingDependency" — prerequisite for Gradle 9.

## Target state

* Gradle **9.7.1** wrapper, built on **Temurin JDK 25** in CI and locally.
* All build plugins and test dependencies on their current latest stable versions.
* eTrice examples/tests/docs on **5.9.0**; `compileOnly` minimum remains **3.0.0**.
* Plugin consumers: decide the documented minimum Gradle version (phase 5 decision).

## Phase 0 — Safety net (no functional change)

1. Record the baseline: `JAVA_HOME=<jdk17> ./gradlew build buildSite` on the current
   Gradle 7.6 must pass (verified 2026-09).
2. Add Gradle's upgrade feedback loop to CI (optional but recommended): run one CI job with
   `--warning-mode=fail` (the TestKit tests already enforce this) so new deprecations fail early.

## Phase 1 — Build tooling refresh on Gradle 7.6 (low risk)

All chosen versions support Gradle 7.6 *and* 9.x, so they can land before the Gradle jump.

| Change | File | From → To |
| --- | --- | --- |
| axion-release | `settings.gradle` | 1.20.1 → 1.21.3 |
| plugin-publish | `settings.gradle` | 1.3.1 → 2.2.1 |
| JUnit Jupiter | `gradle/libs.versions.toml` | 5.13.4 → 5.14.4 (stay on 5.x for now) |
| actions/checkout | `.github/workflows/*.yml` | v4 → v7 |
| actions/setup-java | `.github/workflows/*.yml` | v4 → v6 |
| peaceiris/actions-gh-pages | `.github/workflows/publish.yml` | v3 → v4 |

Notes:

* plugin-publish 2.x auto-applies `java-gradle-plugin` and `maven-publish` (already applied
  here) and removes the `mavenCoordinates`/`withDependencies` blocks (not used here).
* plugin-publish ≥ 2.1 deprecates publishing **without** a feature declaration. Add to
  `gradlePlugin { plugins { … } }` in `subprojects/de.protos.etrice.gradle/build.gradle`:

  ```groovy
  compatibility {
      features {
          configurationCache = false  // declare false until verified, then flip
      }
  }
  ```

* Verify a dry publish locally: `./gradlew publishPlugins --dry-run` (needs portal credentials
  only for the real run). Then do a real `release-X.Y.Z` to prove the pipeline before the
  Gradle upgrade.

## Phase 2 — Gradle 7.6 → 8.14.5 (deprecation buffer)

Purpose: flush out deprecations on a major-but-compatible line before the 9.x jump.
Gradle 8.14.x runs on JVM 8–24 (not 25), so keep CI on JDK 17 or 21 for this phase.

1. With JDK 17: `./gradlew wrapper --gradle-version 8.14.5`
   (also refreshes `gradle/wrapper/gradle-wrapper.jar`/`gradlew` scripts — commit them).
2. `./gradlew build buildSite --warning-mode=fail` and fix every deprecation. Expected
   candidates in this codebase (from the 7.6 → 8.x deprecation lists):
   * `doc/build.gradle`: bare `task unzipJavadoc { … }` syntax and `project.sync`/`zipTree`
     inside `doLast` — migrate to `tasks.register` and consider `providers`/`archiveOperations`.
   * `subprojects/build.gradle`: `options.compilerArgs.addAll` and `javadoc` config are fine;
     check `JavaVersion.current()` guards.
3. Functional tests already run TestKit builds with `--warning-mode=fail`, so the generated
   test projects double as a deprecation harness.
4. Run the full test suite (`./gradlew test`) — it downloads real eTrice 5.4.0 generators.

## Phase 3 — Gradle 8.14.5 → 9.7.1 + JDK 25

The actual modernization step. Java 25 needs Gradle ≥ 9.1.0, hence 9.7.1.

1. `./gradlew wrapper --gradle-version 9.7.1` (still with JDK 17/21).
2. Update CI to JDK 25: `.github/workflows/build.yml` and `publish.yml`
   `actions/setup-java` → `java-version: '25'`.
3. Build and fix Gradle 9 removals. Audited usages in this repo that are **safe** under 9.x:
   * `WorkerExecutor.processIsolation` / `WorkQueue` (GenerateTask) — stable.
   * `SourceTask`, `ConfigurableFileCollection`, `MapProperty`, `NamedDomainObjectContainer`,
     `TaskProvider` — stable.
   * `ExecOperations.javaexec` (EtUnitConvertTask), `ArchiveOperations.zipTree` +
     `FileSystemOperations.sync` (UnzipTask) — stable.
   * Attribute compatibility/disambiguation rules (`ETriceBasePlugin`) — stable.
   * `DependencyHandler.create(FileCollection)` for `modelpathDir` — still supported
     (file collection dependencies; the removed `SelfResolvingDependency` is not referenced).
   * Risk watch-list: eager `.get()` inside the `EtUnitConvertPlugin` container factory
     (intentional, keep), `project.files(provider)` wiring, `Zip.setDuplicatesStrategy`.
4. Documentation build: `org.asciidoctor.jvm.convert` 4.0.5 is Gradle 9 ready (no change).
   Re-check `asciidoctor { outputDir = layout.buildDirectory.dir(…) }` stays lazy.
5. `subprojects/build.gradle`:
   * Keep `options.release = 8` for now (phase 4 decides the long-term baseline).
   * Update javadoc links from `javase/17` to `javase/25` once building on JDK 25.
6. Acceptance: `./gradlew build buildSite` green on JDK 25 locally **and** in CI, and
   TestKit tests (which inherit Gradle 9.7.1) pass with `--warning-mode=fail`.
7. Optional hardening: add a second CI leg on Gradle 8.14.5 via
   `GradleRunner.withGradleVersion("8.14.5")` to guard plugin consumers that haven't
   upgraded yet.

## Phase 4 — Decide the plugin baseline (semver decision)

The plugins are compiled against the building Gradle's API. Today the docs state
"requires at least Gradle {version-gradle}" (= 7.6). After phase 3 that statement becomes 9.7.1.

Two coherent options:

* **A. Conservative (recommended): keep `options.release = 8` and keep supporting Gradle 7.6+
  consumers.** The codebase uses no post-7.6 APIs, so the published plugin keeps working on
  7.6/8.x builds. Keep the docs statement at 7.6 (set `version-gradle` attribute explicitly
  in `doc/build.gradle` instead of `gradle.gradleVersion`). No major version bump required.
* **B. Aggressive: require Gradle 9 / Java 17.** Bump `options.release` to 17, update the docs,
  release as **3.0.0** (breaking change). Only worth it if new Gradle-9-only APIs or
  configuration-cache support are needed.

Either way, keep the `--add-opens java.base/java.lang=ALL-UNNAMED` workaround for older
eTrice/Xtext versions in `GenerateTask` (still required for Java 9+ runtimes).

## Phase 5 — Library refresh

1. **eTrice 5.4.0 → 5.9.0** in all three places (keep consistent, see AGENTS.md):
   * `FunctionalTests.groovy` `etriceVersion`
   * `doc/build.gradle` attribute `version-etrice`
   * `EtUnitConvertPlugin.ETUNIT_CONVERTER_DEFAULT_DEPENDENCY`
   * Leave the `compileOnly` `org.eclipse.etrice.generator.base:3.0.0` untouched
     (documented minimum supported eTrice version).
2. **JUnit 5.14.4 → 6.1.3** (optional, separate PR): JUnit 6 requires Java 17+ at test
   runtime — fine once CI is on JDK 25. The test code only uses `@Test` and JUnit 5-style
   assertions, so the migration is small; run the full suite to confirm.
3. Re-render and publish docs (`buildSite`) so the version attributes in the examples update.

## Phase 6 — Release

1. Publish the upgraded build as a new release. If phase 4 chose option B, tag `release-3.0.0`;
   otherwise `release-2.5.0` (axion-release derives the number, `./gradlew
   release -prereleaseBase` workflows may apply — follow the existing tag process:
   `git tag release-X.Y.Z && git push origin release-X.Y.Z`).
2. After publishing, verify the plugin portal page renders the new version and the docs site
   is updated. Confirm `GRADLE_PUBLISH_KEY`/`GRADLE_PUBLISH_SECRET` secrets still work —
   plugin-publish 2.x changed key handling (`GRADLE_PUBLISH_KEY`/`SECRET` env vars or the
   `login` task).

## Risk register

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Hidden Gradle 9 removals surface only at test time | Medium | TestKit functional tests run real builds with `--warning-mode=fail`; run twice, once with `--rerun-tasks` |
| axion-release version derivation changes behavior | Low | 1.21.x tested on Gradle 9; verify `./gradlew currentVersion` before tagging |
| plugin-publish 2.x rejects upload (missing compatibility declaration) | Medium | Add `compatibility { features { … } }` in phase 1; do one real release before the Gradle jump |
| Consumers stuck on Gradle 7/8 | High impact | Phase 4 option A keeps them supported; document the matrix in README |
| eTrice 5.9.0 generator behavior differs from 5.4.0 | Low | Functional tests generate real code and assert outputs; bump with tests, not blindly |
| GitHub Actions v7 checkout / v4 gh-pages behavior changes | Low | Both workflows are simple; verify one publish run |

## Quick reference — files touched per phase

* Phase 1: `settings.gradle`, `gradle/libs.versions.toml`, `.github/workflows/*.yml`,
  `subprojects/de.protos.etrice.gradle/build.gradle` (compatibility block)
* Phase 2/3: `gradle/wrapper/gradle-wrapper.properties` (+ wrapper jar/scripts),
  `.github/workflows/*.yml` (JDK 25), possibly `doc/build.gradle`, `subprojects/build.gradle`
* Phase 4: `doc/build.gradle` (`version-gradle`), `subprojects/build.gradle` (`options.release`),
  `doc/src/docs/asciidoc/index.adoc` (requirements wording)
* Phase 5: `FunctionalTests.groovy`, `doc/build.gradle`, `EtUnitConvertPlugin.java`,
  `gradle/libs.versions.toml`
