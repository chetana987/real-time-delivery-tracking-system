# CI/CD pipeline

This document explains the GitHub Actions CI pipeline for the delivery-tracking
project: the workflow file, every YAML section, why CI exists, how it maps onto
the Maven lifecycle, common failures, and how the pipeline can grow into a full
CI/CD solution.

Workflow file: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

---

## 1. Why GitHub Actions

- **Zero infrastructure to run builds.** Builds execute on GitHub's hosted
  runners (Linux/macOS/Windows, Docker included), so a repository needs nothing
  but a workflow file.
- **Event-driven and native to the repo.** Workflows trigger automatically on
  Git events (`push`, `pull_request`), so every change gets checked without
  wiring up an external CI server.
- **Cheap and parallel.** Free minutes for public repos, paid-but-cheap for
  private; jobs in a workflow run in parallel by default.
- **Tight GitHub integration.** Status checks appear directly on commits and PRs,
  branch protection can require a green check before merging, and badges are
  one line of Markdown.
- **Marketplace + cache.** Reusable actions (`checkout`, `setup-java`) and
  dependency caching give a production-grade pipeline with a small, auditable
  YAML file.

## 2. Why CI is valuable even if tests pass locally

- **Clean-environment guarantee.** CI builds from a pristine checkout on a fresh
  runner. Locally, artifacts, caches, IDE state, environment variables, and a
  half-modified working tree can mask problems ("works on my machine").
- **The merge happens somewhere else.** Your branch may be green, but what
  matters is that it stays green **when combined with `main`**. PR CI tests the
  integration point continuously instead of once at release time.
- **Consistent tools.** CI pins the JDK (21) and the test conditions, so a
  teammate's different Maven/JDK/Docker setup cannot silently change behavior.
- **Shared, automatic gate.** Anyone can see the build status on any commit;
  it is a reproducible, always-on safety net rather than a one-off local run.
- **Catches environment-sensitive failures.** For example, this project's
  integration tests hit a real MySQL 8 via Testcontainers. Locally those tests
  are skipped when Docker/MySQL are missing — CI actually runs them.

## 3. What a green build proves

A green build is evidence that, in a clean environment with JDK 21 + the pinned
Maven:

- the project **compiles** (main and test sources) with no errors,
- **all unit tests pass** (surefire),
- **all integration tests pass** against a real MySQL 8 container (Testcontainers),
- the **Docker image builds** successfully (and is loaded, not pushed),
- the checked-out revision is therefore a **safe candidate for `main`**.

It does **not** prove the code is bug-free, secure, performant, or deployable —
only that the automated checks for that commit are green.

## 4. What a red build means

A red build means at least one step failed or timed out. Usually:

- a **compilation error** (bad code on the branch),
- a **failing test** (a regression or a flaky test),
- a **broken Docker build**,
- an **infrastructure/runner issue** (transient, e.g. network or image pull).

Red on a PR is a signal to the author: do not merge until it is green. With
branch protection, merging is actually blocked. A red build on `main` should be
treated as an incident — the fix typically takes priority over new work.

## 5. The workflow file, section by section

### `name: CI`

Display name shown in the Actions tab and in the badge URL.

### `on:`

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:
```

- `push` + `branches: [main]` — every push to `main` triggers a run.
- `pull_request` + `branches: [main]` — every PR targeting `main` triggers a run
  against the PR's head commit.
- `workflow_dispatch` — lets you start a run manually from the Actions tab
  (useful for debugging a "works locally but not in CI" failure).

### `concurrency`

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

If a new run starts for the same workflow + branch/PR, the older in-progress run
is cancelled. Prevents queueing stale builds when you push again quickly.

### `permissions`

```yaml
permissions:
  contents: read
```

Least privilege. This workflow only needs to read the repository; it never gets
a write-scoped token.

### `jobs.build`

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
```

- `runs-on: ubuntu-latest` — a clean Linux runner with Docker pre-installed
  (required by Testcontainers).
- `timeout-minutes: 30` — a hung Maven/download cannot burn runner minutes.

Steps:

1. **Checkout repository** — `actions/checkout@v4` copies the commit onto the
   runner.
2. **Set up JDK 21** — `actions/setup-java@v4` installs Temurin JDK 21,
   matching `<java.version>21</java.version>` in `pom.xml`.
3. **Cache Maven dependencies** — `actions/cache@v4` caches `~/.m2/repository`.
   The key includes `hashFiles('**/pom.xml')`, so the cache is reused while the
   POM is unchanged; `restore-keys` still allows a partial hit after a POM edit.
4. **Run `mvn clean verify`** — the actual build. `-B` (batch mode, no prompts),
   `-ntp` (no transfer progress). Maven's non-zero exit code fails the step,
   the job, and therefore the workflow: **compile error or any failing test
   fails CI immediately.**
5. **Upload test reports on failure** — only when the build failed
   (`if: failure()`), attach `target/surefire-reports` so the failing test can
   be inspected from the run page.

### `jobs.docker-build`

```yaml
needs: build
```

Only runs after `build` is green (fail-fast: no point building the image if
tests already failed).

- `docker/setup-buildx-action@v3` — prepares the BuildKit builder.
- `docker/build-push-action@v6` with `push: false` — **builds only**; the image
  is loaded into the local daemon (`load: true`) to prove it is valid, then
  discarded. `cache-from: type=gha` / `cache-to: type=gha` reuse the GitHub
  Actions cache so unchanged layers are not rebuilt.
- The image is **never pushed** to Docker Hub or any registry; no credentials
  are needed.

## 6. How GitHub Actions integrates with the Maven lifecycle

GitHub Actions does not know what Maven is. A `run:` step is just a shell
command; the integration is purely: *exit code 0 = step passed, non-zero =
step failed.* Maven is the part that enforces the ordering — the lifecycle.

### Maven lifecycle phases (default binding)

| Phase      | Runs before | What it does                                   |
|------------|-------------|------------------------------------------------|
| `compile`  | —           | Compiles `src/main/java` into `target/classes` |
| `test`     | `compile`   | Compiles test sources and runs them (surefire) |
| `package`  | `test`      | Produces the distributable artifact (`target/*.jar`) |
| `verify`   | `package`   | Runs additional checks to confirm the package is valid (integration tests via failsafe, JaCoCo checks, etc.) |
| `install`  | `verify`    | Copies the artifact into the local repository `~/.m2/repository` |
| `deploy`   | `install`   | Uploads the artifact to a remote repository    |

Key rule: **phases are sequential** — invoking `verify` automatically runs
`compile`, `test`, and `package` first. `clean` is a lifecycle of its own that
deletes `target/`.

Practical differences:

- `compile` — fastest check; catches only syntax/type errors in main code.
- `test` — the everyday "are my tests green" command; does **not** package.
- `package` — what the Dockerfile uses (`-DskipTests package`) to produce the
  runnable jar inside the image.
- `verify` — the "ready to trust this build" command: everything up to and
  including the package **plus** deeper verification. **CI runs `verify`**
  because it is strictly stronger than `test`/`package`.
- `install` — used locally when another local module consumes the artifact;
  not needed in this single-module CI.

In this project, `mvn clean verify`:

1. `clean` wipes `target/`;
2. compiles main sources;
3. compiles tests and runs them (surefire) — unit tests plus the
   `@SpringBootTest` integration tests, which start a real MySQL 8 container
   through Testcontainers;
4. packages the Spring Boot fat jar;
5. generates the JaCoCo coverage report (bound to the `test` phase in `pom.xml`).

Any failure at any of these steps exits non-zero → the `build` job fails → the
workflow is red.

## 7. Common CI failures and how to debug them

| Failure | Likely cause | How to debug |
|---|---|---|
| Compilation errors | Bad code on the branch; JDK mismatch | Read the `[ERROR]` lines in the step log; fix and re-push; verify JDK version matches `setup-java` |
| Failing test | Regression, race, or flaky/timezone test | Open the **Upload test reports** artifact → `*.txt`/`*.xml` in `target/surefire-reports`; reproduce locally with the same JDK |
| Integration tests abort/skip | Docker unavailable (or `mysql:8.0` pull failing) | Confirm `runs-on: ubuntu-latest` (Docker present); check Testcontainers logs in the step log |
| Hung build | Maven waiting on input, slow network | `-B` avoids prompts; `timeout-minutes` kills runaway steps; check the step log for where it stalls |
| Docker build fails | Dependency download inside build stage; layer/cache issue | Inspect the docker-build job log; clear the GHA cache via Actions → Caches if a stale layer is suspected |
| Cache not helping | POM changed → new cache key | Expected: `restore-keys` gives a partial hit; verify the cache step reported "Cache restored" |
| Environment drift | Different Maven/JDK locally vs runner | Reproduce locally with JDK 21 + the runner's Maven (or the wrapper if added) |
| YAML syntax error | Workflow file malformed | GitHub validates it before running; use a local linter, or the Actions tab shows the parse error |

General debugging loop: find the failing step → read its log → download the
reports artifact → reproduce locally (ideally via a manual `workflow_dispatch`
run) → fix → push.

## 8. Folder structure

```
.github/
└── workflows/
    └── ci.yml          # the entire CI pipeline
```

## 9. Instructions to enable GitHub Actions

1. Push the repository (including `.github/workflows/ci.yml`) to GitHub.
2. Confirm Actions are enabled: **Settings → Actions → General → Permissions →
   "Allow all actions and reusable workflows"** (default) and **Actions
   permissions → "Read repository contents and packages permissions"** — the
   workflow only needs read access.
3. Open the **Actions** tab — the `CI` workflow appears; the first run starts
   automatically on push/PR to `main` (or click **Run workflow** for a manual
   run).
4. Update the badge: replace `OWNER/REPO` in the README badge URL with your
   GitHub repository path, e.g. `https://github.com/yourname/delivery-tracking`.
5. Optional but recommended: enable **branch protection** on `main`
   (Settings → Branches → Add rule) and require the `CI` check to pass before
   merging. That makes the red/green gate enforceable, not just advisory.

## 10. Expected workflow execution sequence

For a push to `main` (or a PR targeting `main`):

1. GitHub creates a workflow run for the pushed commit.
2. `concurrency` cancels any in-progress run for the same branch/PR.
3. **`build` job** starts on `ubuntu-latest`:
   checkout → JDK 21 → Maven cache restore →
   `mvn clean verify` (compile → unit + Testcontainers integration tests →
   package → JaCoCo report).
   - If it fails → workflow is red; reports are uploaded; `docker-build` is skipped.
4. **`docker-build` job** starts only if `build` is green:
   checkout → Buildx → `docker build` with GHA layer cache → image loaded locally.
5. All jobs green → workflow is green; the PR shows a green check and is
   eligible to merge under branch protection.

Typical wall-clock: ~2–4 min for `build` (first run longer due to image/dependency
downloads, later runs much faster thanks to caches) plus ~1–2 min for the image.

## 11. Current limitations

- **No local Maven verification on this machine** — the pipeline itself has not
  been executed yet; the first GitHub run is the real validation.
- **No Maven wrapper (`mvnw`)** — CI relies on the runner's Maven (3.9.x);
  only the Docker build stage pins Maven 3.9. Locally, the exact Maven version
  can vary between developers.
- **Backend only.** The React `frontend/` (Vite) is neither built, linted, nor
  tested, and the `loadtest/` k6 suite is not run.
- **No coverage gate.** JaCoCo produces a report but there is no threshold
  enforcing minimum coverage.
- **Docker job verifies the build only** — the image is not started, so there is
  no smoke test against MySQL/Redis and no `/api/health` probe in CI.
- **Testcontainers covers MySQL only**; there is no containerized Redis for
  integration tests.
- **Badge placeholder** must be replaced with the real `OWNER/REPO`.
- **No CD** — no artifact publishing, no deployment, no release workflow.
- **Fork PRs** do not get cache writes and do not expose repository secrets
  (by design); first runs from forks are slower.
- **`ubuntu-latest` is a moving target** — runner images update over time.

## 12. Evolving into a full CI/CD pipeline

Near term (CI hardening):

- Add the **Maven wrapper** (`mvnw`) for fully deterministic builds.
- Add a **frontend job**: `npm ci` → `npm run build` → lint → unit tests.
- Add **Testcontainers Redis** so the caching/geolocation paths are tested too.
- Enforce a **JaCoCo coverage threshold** and publish coverage to Codecov/Sonar.
- Add **security/quality gates**: dependency scanning (OWASP Dependency-Check /
  `osv-scanner`), `hadolint` for the Dockerfile, gitleaks for secrets, SpotBugs/Checkstyle.
- Run **k6 load tests** against a staging deployment and fail on regressions.

Later (CD):

- Publish the built jar/OCI image as a **release artifact** (GitHub Packages or
  ECR) with a versioned tag, gated on `verify`.
- Deploy **automatically on `main` merge** to a staging environment and
  **on version tags** to production, using **GitHub Environments** with
  approval gates for prod.
- Use the **compose smoke test** (start MySQL + Redis + backend, probe
  `/api/health`) as a deployment readiness check, with zero-downtime rollout
  (rolling or blue/green) and automatic rollback on failed health checks.
- Add **notifications** (status checks, Slack/Discord/email) and a nightly
  scheduled build to catch slow environmental drift.

The current pipeline is deliberately small and single-purpose: a reliable,
green/red gate for the backend. Every suggestion above plugs into the same
structure without changing what exists today.
