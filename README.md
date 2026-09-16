# eTrice Gradle Plugins

[![build](https://github.com/protossoftware/etrice-gradle-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/protossoftware/etrice-gradle-plugin/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/de.protos.etrice-base)](https://plugins.gradle.org/plugin/de.protos.etrice-base)
[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](LICENSE)

Build [eTrice](https://www.eclipse.org/etrice/) projects with [Gradle](https://gradle.org/):
generate code from ROOM models, consume model and runtime libraries from Maven repositories,
and convert etUnit test reports — all integrated in a regular Gradle build.

## Overview

[eTrice](https://www.eclipse.org/etrice/) is a project for the model-driven development of
distributed, concurrent systems. This repository provides a family of Gradle plugins that
integrate the eTrice code generators into Gradle builds:

* run eTrice generators (C, C++, Java, doc, …) in isolated worker processes
* resolve generator, model and runtime dependencies from Maven repositories
* share models between Gradle projects (modelpath handling, model zips)
* generate an Eclipse `.modelpath` file for IDE integration
* convert etUnit test reports to XML for CI consumption

## Provided Plugins

| Plugin ID | Name | Purpose |
| --- | --- | --- |
| [`de.protos.etrice-base`](https://plugins.gradle.org/plugin/de.protos.etrice-base) | eTrice Base Plugin | Core setup: `modelSet` extension, `generator`/`modelpath` configurations, generate tasks, `zipModel`, `eclipseModelpath` |
| [`de.protos.etrice-c`](https://plugins.gradle.org/plugin/de.protos.etrice-c) | eTrice C Plugin | Preconfigured eTrice C project (applies the base plugin with the `etrice-c` generator) |
| [`de.protos.etrice-java`](https://plugins.gradle.org/plugin/de.protos.etrice-java) | eTrice Java Plugin | Preconfigured eTrice Java project (applies the base plugin with the `etrice-java` generator) |
| [`de.protos.model-library`](https://plugins.gradle.org/plugin/de.protos.model-library) | Model Library Plugin | Download and extract model zips (`modelLibrary` configuration, `unzipModel` task) |
| [`de.protos.source-publish`](https://plugins.gradle.org/plugin/de.protos.source-publish) | Source Publish Plugin | Package sources into a zip and publish them via the `adhoc` component |
| [`de.protos.source-library`](https://plugins.gradle.org/plugin/de.protos.source-library) | Source Library Plugin | Download and extract source zips (`sourceLibrary` configuration, `unzipSource` task) |
| [`de.protos.etunit-convert`](https://plugins.gradle.org/plugin/de.protos.etunit-convert) | etUnit Convert Plugin | Convert etUnit (`.etu`) test reports to XML test reports |

### Tasks

| Task | Type | Description |
| --- | --- | --- |
| `generate<ModelSet>` / `generate` | `GenerateTask` | Runs the eTrice generator for a model source set (in a forked worker process) |
| `zipModel` | `Zip` | Zips all model files for publishing |
| `eclipseModelpath` | `EclipseModelpathTask` | Writes an Eclipse modelpath file (overwrites existing files!) |
| `unzipModel` | `UnzipTask` | Extracts downloaded model zips into `build/modellib` |
| `zipSource` | `Zip` | Zips sources for publishing |
| `unzipSource` | `UnzipTask` | Extracts downloaded source zips into `build/sourcelib` |
| `<custom>` | `EtUnitConvertTask` | Converts etUnit files to XML (declared in the `etunitConvert` extension) |

Tip: pass `--debug-jvm` to a generate task to start the generator suspended on port 5005 for debugging.

## Getting Started

See the rendered documentation for full details and a complete example:

* [Documentation](https://protossoftware.github.io/etrice-gradle-plugin/)
* [Javadoc](https://protossoftware.github.io/etrice-gradle-plugin/javadoc/)

A minimal eTrice C project:

```gradle
plugins {
    id "de.protos.etrice-c" version "2.4.0"
    id "de.protos.model-library" version "2.4.0"
}

repositories {
    maven {
        url "https://repo.eclipse.org/content/repositories/maven_central/"
    }
    maven {
        url "https://repo.eclipse.org/content/repositories/etrice/"
    }
}

dependencies {
    generator "org.eclipse.etrice:org.eclipse.etrice.generator.c:5.9.0"
    modelLibrary "org.eclipse.etrice:org.eclipse.etrice.modellib.c:5.9.0"
}

modelSet {
    room {
        source.srcDirs "model", unzipModel.destination
        source.include "**/*.room", "**/*.etmap", "**/*.etphys"
    }
}
```

Run the code generation with `./gradlew generateRoom` (or a full `./gradlew build`).

## Requirements

* **Users of the plugins:** Gradle 7.6 or newer. The plugins are compiled for Java 8 and run
  on any JDK supported by your Gradle version (Java 8 – 19 with Gradle 7.6,
  Java 17 – 26 with Gradle 9).
  Generators are executed in worker processes; older eTrice versions (Xtext 2.25 based)
  are made to run on Java 17+ automatically via `--add-opens`.
* **Building this repository:** JDK 17 – 26 (the CI uses Temurin 25 and additionally runs a
  compatibility build on JDK 17 for the minimum supported Gradle version).
  See [UPGRADE_PLAN.md](UPGRADE_PLAN.md) for the current toolchain state.
* eTrice generator artifacts are resolved from the
  [eTrice repositories at Eclipse](https://repo.eclipse.org/content/repositories/etrice/).

## Building from Source

```bash
git clone https://github.com/protossoftware/etrice-gradle-plugin.git
cd etrice-gradle-plugin
./gradlew build          # compiles, runs the functional tests and validates the plugins
./gradlew buildSite      # renders the documentation + javadoc into doc/build/site
```

The functional tests ([FunctionalTests.groovy](subprojects/de.protos.etrice.gradle/src/test/groovy/de/protos/etrice/gradle/FunctionalTests.groovy))
build real temporary Gradle projects via [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
and run actual eTrice generators from the Eclipse repositories. They are executed with
`--warning-mode=fail`, so any Gradle deprecation warning fails the build.

## Project Structure

```
├── build.gradle                  # root build: axion-release versioning, repositories
├── settings.gradle               # plugin version pins, included projects
├── gradle/libs.versions.toml     # version catalog for dependencies
├── doc/                          # AsciiDoc user documentation (rendered with Asciidoctor)
└── subprojects/
    └── de.protos.etrice.gradle/  # the Gradle plugin project itself
        └── src/main/java/de/protos/etrice/gradle/   # plugin sources (see Javadoc)
```

## Releases and Publishing

Versioning is handled by the [axion-release-plugin](https://github.com/allegro/axion-release-plugin)
from git tags with the `release-` prefix. Pushing a tag `release-X.Y.Z` triggers the
[publish workflow](.github/workflows/publish.yml), which publishes the plugins to the
[Gradle Plugin Portal](https://plugins.gradle.org/) and deploys the documentation to
[GitHub Pages](https://protossoftware.github.io/etrice-gradle-plugin/).

## Contributing

Contributions are welcome! Please open an issue or pull request at
[protossoftware/etrice-gradle-plugin](https://github.com/protossoftware/etrice-gradle-plugin).
See [AGENTS.md](AGENTS.md) for the build conventions used in this repository.

## License

This project is licensed under the [Eclipse Public License 2.0](LICENSE).
