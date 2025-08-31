# Contributing to SPI-Tooling

Firstly, thank you for taking the time contribute!
SPI-Tooling exists to make service loading easier, and your ideas, fixes and improvements help make that possible.

This guide will walk you through how to set up your environment, make changes, and submit them in a way that keeps the project reproducible and contributor-friendly.

---

## Table of Contents
1. [Code of Conduct](#code-of-conduct)
2. [Ways to Contribute](#ways-to-contribute)
3. [Development Setup](#development-setup)
4. [Branching & Commit Guidelines](#branching--commit-guidelines)
5. [Testing](#testing)
6. [Pull Request Process](#pull-request-process)
7. [Release Process](#release-process)

---

## Code of Conduct
By participating in this project, you agree to uphold our [Code of Conduct](CODE_OF_CONDUCT.md).
We are committed to a welcoming experience for everyone.

---

## Ways to Contribute
- **Report bugs** - open an issue with clear reproduction steps.
- **Suggest features** - describe the problem you want to solve, not just the solution
- **Improve documentation** - even small clarifications help future contributors
- **Submit code changes** - fix bugs, add features, or improve build automation

---

## Development Setup

### 1. Fork & Clone
```bash
git clone https://github.com/<your-username>/SPI-Tooling.git
cd SPI-Tooling
```

### 2. Configure Git
```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

### 3. Build the project
```bash
./gradlew build
```

### 4. Run all tests
```bash
./gradlew kotest
```

---

## Branching & Commit Guidelines
- Default branch: `main`
- Feature branches: `feature/[issue-<number>/]<short-description>`
- Bugfix branches: `bugfix/[issue-<number>/]<short-description>`
- Chore branches: `task/[issue-<number>/]<short-description>`

Commit messages should follow the [Conventional Commits](https://www.conventionalcommits.org/) style:
```
feat: add support for nested service providers
fix: correct binary name in generated META-INF/services
docs: update README with architecture diagram
```

---

## Testing
We use:
- **Kotest** for expressive, readable tests
- **kotlin-compile-testing** for compile-time validation

Before opening a PR:
```bash
./gradlew clean kotest
```

---

## Pull Request Process
1. Fork the repository
2. Create a branch from `SPI-Tooling/main`
3. Make your changes, keeping builds reproducible (no manual steps)
4. Update any necessary documentation
5. Add additional tests, or adjust tests, if necessary
6. Update the version in `gradle.properties` following [Semantic Versioning](https://semver.org/)
7. Open a pull request against `SPI-Tooling/main` with:
  - A clear title with format: `[type]: <description>`
  - A detailed description of the changes
  - A link to any relevant issues
  - Notes on any breaking changes

---

## Release Process
Releases are automated via GitHub Actions and occur when a push or merge is made to `main`.

---

## Tips for Contributors
- Keep changes focused - small, atomic PRs are easier to review
- If you're unsure about an approach, open a Draft PR early for feedback
- Think about future maintainers - clear code, clear cods, clear tests