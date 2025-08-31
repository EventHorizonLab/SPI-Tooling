# SPI‑Tooling
**Annotation‑driven ServiceLoader automation for JVM projects**

[![Annotations Maven Central](https://img.shields.io/maven-central/v/io.github.eventhorizonlab/spi-tooling-annotations?color=blue)](https://central.sonatype.com/artifact/io.github.eventhorizonlab/spi-tooling-annotations)
[![Processor Maven Central](https://img.shields.io/maven-central/v/io.github.eventhorizonlab/spi-tooling-processor?color=blue)](https://central.sonatype.com/artifact/io.github.eventhorizonlab/spi-tooling-processor) 
[![Build](https://github.com/EventHorizonLab/SPI-Tooling/actions/workflows/release-and-publish.yml/badge.svg)](https://github.com/EventHorizonLab/SPI-Tooling/actions)  
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---
## 📖 Overview
`spi-tooling` eliminates the boilerplate of **Java’s Service Provider Interface (SPI)** by generating `META-INF/services` entries at compile time.  
Instead of manually maintaining service registration files, you annotate your implementations and let the processor handle the rest — **deterministically, reproducibly, and contributor‑friendly**.

This is designed for:
- **Library authors** who want zero‑friction SPI onboarding for consumers.
- **Multi‑module projects** where manual service file management is error‑prone.
- **Teams** that value reproducible builds and automated contributor workflows.

---
## ✨ Features
- **Annotation‑driven**: Mark your contracts with `@ServiceContract` and service implementations with `@ServiceProvider(Contract:class)`
- **Automatic `META-INF/services` generation** at compile time.
- **Multi‑module aware**: Handles aggregation across modules without collisions.
- **Deterministic output**: Stable ordering for reproducible builds.
- **Error validation**: Compile‑time checks for missing interfaces, duplicates, or misconfigurations.
- **Kotlin & Java friendly**: Works seamlessly with both.
- **Gradle‑ready**: Zero manual wiring for most setups.

---
## 🚀 Quick Start

### 1. Add the dependency
Groovy DSL:
```groovy
dependencies {
    implementation "com.github.eventhorizonlab:spi-tooling-annotations:<version>"
    
    // --- BELOW ONLY REQUIRED FOR PROJECTS USING @ServiceProvider ---
    // Kotlin projects:
    kapt "com.github.eventhorizonlab:spi-tooling-processor:<version>"
    
    // Java projects:
    annotationProcessor("com.github.eventhorizonlab:spi-tooling:<version>")
}
```
Gradle kts:
```kotlin
dependencies {
    implementation("com.github.eventhorizonlab:spi-tooling-annotations:<version>")
    
    // --- BELOW ONLY REQUIRED FOR PROJECTS USING @ServiceProvider ---
    // Kotlin projects:
    kapt("com.github.eventhorizonlab:spi-tooling-processor:<version>")
    // Java projects:
    annotationProcessor("com.github.eventhorizonlab:spi-tooling-processor:<version>")
}
```
➡️ See [Kapt](https://kotlinlang.org/docs/kapt.html#0) for more information

### 2. Annotate your contract & implementation
Contract:
```kotlin
package com.example.myproject.spi.api
import com.github.eventhorizonlab.spi.ServiceContract

@ServiceContract
interface MyContract {
    fun doSomething()
}
```
Implementation:
```kotlin
package com.example.myproject.spi
import com.github.eventhorizonlab.spi.ServiceProvider
import com.example.myproject.spi.api.MyContract

@ServiceProvider(MyContract::class)
class MyImpl : MyContract {
    override fun doSomething() {
        println("Hello World!")
    }
}
```
### 3. Build your project
The processor generates:
```
META-INF/services/com.example.myproject.spi.api.MyContract
```
containing:
```
com.example.myproject.spi.MyImpl
```

### 4. Use ServiceLoader

```kotlin
package com.example.myproject.spi.api
import com.github.eventhorizonlab.spi.ServiceContract
import java.util.*

@ServiceContract
interface MyContract {
    fun doSomething()
}

val myContract = ServiceLoader.load(MyContract::class.java).first()
myContract.doSomething()
```

---

# Architecture Diagram
```
 ┌─────────────────────────┐
 │  Source Code (.kt/.java)│
 │  with annotations       │
 └───────────┬─────────────┘
             │
             ▼
 ┌──────────────────────────┐
 │  SPI-Tooling Annotation  │
 │  Processor (compile-time)│
 └───────────┬──────────────┘
             │
             ▼
 ┌───────────────────────────────┐
 │  Generates META-INF/services/ │
 │  <FullyQualifiedInterfaceName>│
 └───────────────────────────────┘

```

---
# Testing & Validation
`spi-tooling` is tested with:
- **kotest** for expressive, readable test cases
- **kotlin-compile-testing** for compile-time validation
Run tests locally:
```bash
./gradlew kotest
```

---
# Development

## Clone & Build
```bash
git clone https://github.com/EventHorizonLab/SPI-Tooling.git
cd SPI-Tooling
./gradlew build
```

## Contributing
We welcome contributions!
- Keep builds reproducible -- no manual steps
- Add tests for new features
- Follow the existing Gradle/Kotlin DSL style
See [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

---
# Publishing
This project is published to **Maven Central** and **GitHub Packages** under:
```
groupId: io.github.eventhorizonlab
artifactIds:
- spi-tooling-annotations
- spi-tooling-processor
```
Releases are automated via GitHub Actions:
- On push/merge to `main` & `gradle.properties` updated with new version
- Pushes a new version tag -> Publishes to Maven Central & GitHub Packages -> Creates a GitHub Release
GPG signing and Sonatype credentials are securely handled in CI

---
# License
[MIT License](LICENSE.md)

---
# Why SPI-Tooling?
Manual SPI file management is:
- Error-prone (typos, missing entries)
- Hard to maintain in multi-module setups
- A barrier for new contributors
`spi-tooling` makes it automatic, safe, and reproducible -- so you can focus on building features, not maintaing service files.