# GitHub Actions Scripts

This directory contains scripts used by GitHub Actions workflows for the Spring AI project.

## test_discovery.py

A Python script that determines which Maven modules are affected by changes in a PR or push, enabling efficient CI builds that only test modified code.

### Usage

```bash
# Basic usage (auto-detects context)
python3 .github/scripts/test_discovery.py modules-from-diff

# With explicit base reference (for maintenance branches)
python3 .github/scripts/test_discovery.py modules-from-diff --base origin/1.0.x

# With verbose logging (debugging)
python3 .github/scripts/test_discovery.py modules-from-diff --verbose

# Combined options
python3 .github/scripts/test_discovery.py modules-from-diff --base origin/1.0.x --verbose
```

### CLI Options

- `--base <ref>`: Explicit base reference for git diff (e.g., `origin/1.0.x`)
- `--verbose`: Show detailed logging to stderr including detected base, changed files, and final module list

### Output

- **Empty string**: No modules affected (documentation/config changes only)
- **Comma-separated list**: Module paths suitable for `mvn -pl` parameter

### Examples

```bash
# Single module affected
$ python3 .github/scripts/test_discovery.py modules-from-diff
vector-stores/spring-ai-qdrant-store

# Multiple modules affected  
$ python3 .github/scripts/test_discovery.py modules-from-diff
vector-stores/spring-ai-qdrant-store,models/spring-ai-openai

# No code changes (docs only)
$ python3 .github/scripts/test_discovery.py modules-from-diff

# Verbose output (to stderr)
$ python3 .github/scripts/test_discovery.py modules-from-diff --verbose
vector-stores/spring-ai-qdrant-store
Detected base ref: origin/main (merge-base)
Changed files (2):
  - vector-stores/spring-ai-qdrant-store/src/main/java/QdrantVectorStore.java
  - vector-stores/spring-ai-qdrant-store/src/test/java/QdrantTests.java
Final module list: vector-stores/spring-ai-qdrant-store

# Maintenance branch with explicit base
$ python3 .github/scripts/test_discovery.py modules-from-diff --base origin/1.0.x
vector-stores/spring-ai-qdrant-store
```

### Integration with GitHub Actions

#### PR-based builds (`_java-build` reusable workflow):

```yaml
- name: Compute impacted modules (optional)
  id: mods
  if: inputs.mode == 'impacted'
  run: |
    MODS=$(python3 .github/scripts/test_discovery.py modules-from-diff)
    echo "modules=$MODS" >> $GITHUB_OUTPUT

- name: Build
  run: |
    case "${{ inputs.mode }}" in
      impacted)
        MODS="${{ steps.mods.outputs.modules }}"
        ./mvnw -B -T 1C -DskipITs -DfailIfNoTests=false -pl "${MODS}" -amd verify
        ;;
    esac
```

#### Maintenance branch fast builds (`maintenance-fast.yml`):

```yaml
- name: Compute impacted modules
  id: mods
  run: |
    MODS=$(python3 .github/scripts/test_discovery.py modules-from-diff --base "origin/$GITHUB_REF_NAME" --verbose)
    echo "modules=$MODS" >> $GITHUB_OUTPUT

- name: Fast compile + unit tests
  run: |
    MODS="${{ steps.mods.outputs.modules }}"
    if [ -z "$MODS" ]; then MODS="."; fi
    ./mvnw -B -T 1C -DskipITs -DfailIfNoTests=false -pl "$MODS" -amd verify
```

### Algorithm

The script:

1. **Detects changed files** using `git diff` against the appropriate base branch
2. **Maps files to Maven modules** by walking up directory tree to find `pom.xml`
3. **Filters relevant files** (Java source, tests, resources, build files)
4. **Returns module paths** in Maven-compatible format

### Environment Variables

The script automatically detects the CI context using:

- `GITHUB_BASE_REF`: Base branch for PR builds  
- `GITHUB_HEAD_REF`: Head branch for PR builds
- `GITHUB_REF_NAME`: Current branch for push builds (maintenance branches)
- Falls back to `origin/main` merge base when context unclear

### Context Detection Logic

1. **Explicit `--base`**: Use provided reference directly
2. **PR Context**: Compare against `origin/$GITHUB_BASE_REF`
3. **Push Context**: Compare against `origin/$GITHUB_REF_NAME`
4. **Fallback**: Find merge base with `origin/main`

### Error Handling

- Returns empty string on errors to gracefully fall back to full builds
- Logs errors to stderr for debugging
- Never fails the CI pipeline due to discovery issues

## Fast Maintenance Branch Workflow

### Overview

The `maintenance-fast.yml` workflow provides efficient CI builds for maintenance branch cherry-picks:

- **Triggers**: Only on pushes to `*.*.x` branches (e.g., `1.0.x`, `1.1.x`)
- **Cherry-pick Guard**: Job-level guard prevents runner startup unless commit message contains "(cherry picked from commit"
- **Fast Execution**: Unit tests only (skips integration tests)
- **Smart Targeting**: Only tests affected modules using test discovery

### Features

- **Job-level Guard**: `if: contains(github.event.head_commit.message, '(cherry picked from commit')` 
- **Explicit Base**: Uses `--base origin/$GITHUB_REF_NAME` for accurate multi-commit diff
- **Verbose Logging**: Shows commit range and detailed test discovery output
- **Safe Fallback**: Compiles root (`.`) if no modules detected
- **Concurrency Control**: Cancels superseded runs automatically

### Example Output

```
Base ref: origin/1.0.x
3b59e6840 test: Enhances test coverage for QdrantObjectFactory.toObjectMap

Detected base ref: origin/1.0.x
Changed files (1):
  - vector-stores/spring-ai-qdrant-store/src/test/java/org/springframework/ai/vectorstore/qdrant/QdrantObjectFactoryTests.java
Final module list: vector-stores/spring-ai-qdrant-store

[INFO] Building Spring AI Qdrant Store 1.0.1-SNAPSHOT
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Safety Measures

- **Cherry-pick Only**: Won't run on manual pushes to maintenance branches
- **Nightly Safety Net**: Full integration test builds still run daily
- **Error Handling**: Falls back to root compilation if module detection fails
- **Minimal Permissions**: `contents: read` only