# Ecosystem CI Dashboard

This directory contains the configuration for the Spring AI Ecosystem CI Dashboard, which monitors the CI health of key ecosystem dependencies.

## Overview

The dashboard workflow runs daily and:
1. Queries CI status for each monitored repository
2. Updates the [Wiki Dashboard](https://github.com/spring-projects/spring-ai/wiki/Ecosystem-CI-Dashboard) with current status
3. Posts alerts to [Issue #5334](https://github.com/spring-projects/spring-ai/issues/5334) when dependencies fail for extended periods

## Subscribing to Alerts

To receive notifications when ecosystem dependencies fail CI:

1. Go to [Issue #5334](https://github.com/spring-projects/spring-ai/issues/5334)
2. Click **Subscribe** in the right sidebar
3. You'll receive GitHub notifications when alerts are posted

## Alert Policy

| Condition | Action |
|-----------|--------|
| Dependency fails (day 1) | Dashboard updated, no alert |
| Dependency fails for 2+ days | Alert comment posted (triggers notification) |
| Dependency recovers | Dashboard updated, no alert |

The `alert_after_days` setting in `ci-alert-config.json` controls the threshold (default: 2 days).

## Adding or Removing Dependencies

Edit `ci-alert-config.json`:

```json
{
  "dependencies": [
    { "owner": "org-name", "repo": "repo-name" },
    ...
  ]
}
```

Each dependency must have:
- `owner`: GitHub organization or user
- `repo`: Repository name

The workflow monitors the branch specified by `tracked_branch` (default: `main`).

## Configuration Reference

| Field | Description | Default |
|-------|-------------|---------|
| `issue_number` | GitHub issue for dashboard and alerts | 5334 |
| `tracked_branch` | Branch to monitor for each repo | main |
| `alert_after_days` | Days of failure before alerting | 2 |
| `heartbeat_days` | Minimum days between repeat alerts | 1 |

## Manual Trigger

To run the dashboard manually:

1. Go to [Actions > Ecosystem CI Dashboard](https://github.com/spring-projects/spring-ai/actions/workflows/dependency-ci-dashboard.yml)
2. Click **Run workflow**
3. Select the `main` branch and click **Run workflow**

## Files

- `ci-alert-config.json` - Dashboard configuration
- `../../.github/workflows/dependency-ci-dashboard.yml` - GitHub Actions workflow
