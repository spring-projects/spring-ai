# Spring AI agent guidelines

Concise extract of [CONTRIBUTING.md](CONTRIBUTING.md), which remains the reference for humans.

## Build

- JDK 17 with `-XDaddTypeAnnotationsToSymbol` support (see `.sdkmanrc`, e.g. `17.0.19-librca`), native CPU architecture.
- Build and unit tests: `./mvnw clean package` (scope with `-am -pl <module>`).
- Format sources: `./mvnw process-sources` (enforced by CI, does not fix import order).
- Local builds reformat sources automatically: add `-Dspring-javaformat.skip=true` to disable it, or `-P'!format-apply,format-check'` to fail on violations without modifying files.
- Integration tests for a module: `./mvnw -am -pl <module> -Pintegration-tests verify`.
- Single integration test: add `-Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=<TestClassIT>`; tests needing a missing provider API key are skipped.
- Javadoc check: `./mvnw javadoc:javadoc`. Reference docs: `./mvnw -pl spring-ai-docs antora`.
- Maven build cache is enabled; if the build breaks for no clear reason, retry with `-Dmaven.build.cache.enabled=false`.

## Code style

- Follow Spring Framework code style; match surrounding code and do not reformat unrelated code.
- Tabs, LF, UTF-8, no trailing whitespace; wrap Javadoc at 90 and code at ~120 characters.
- Import order, groups separated by a blank line: `java.*`, then `javax.*` + `jakarta.*`, then others, then `org.springframework.*`, then static imports.
- No wildcard imports. No static imports in production code except constants/enum constants and third-party DSL factory methods; use them in tests (e.g. `assertThat`).
- Every source file: Apache 2.0 license header (`Copyright 2023-present the original author or authors.`, copy from an existing file), package, imports, exactly one top-level class.
- Add `@since` on new public types and methods (e.g. `@since 2.0.0`).
- Add or update tests for any code change.
- In `.adoc` and `.md` files, write one sentence per line.

## Commits and pull requests

- Title: imperative, capitalized verb, ideally under 50 characters, no `fix:`/`docs:` prefixes and no issue/PR number.
- Body wrapped at ~72 characters, followed by `Fixes #123`/`Closes #123` (or `See #123`), then a `Signed-off-by: Name <email>` trailer (DCO, use `git commit -s`).
- Enclose annotations in backticks in commit messages to avoid mentioning GitHub users.
- PR titles feed the changelog: short, descriptive, types or file names in backticks; reference related issues in the description, not the title.
