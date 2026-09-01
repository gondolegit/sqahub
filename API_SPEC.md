# SQAHub Backend — API Spec for Frontend Implementation

> **How to use this file:** paste this whole document into Claude Code inside the `sqahub-fe` project and ask it to implement API client functions / hooks / pages for these endpoints. Everything below is derived directly from the backend source code (`sqahub` repo) as of the `main` branch — controllers, DTOs, and `SecurityConfiguration`.

## Prompt to paste into Claude Code (sqahub-fe)

```
Implement a typed API client for the SQAHub backend described below. For each resource:
1. Create request/response TypeScript types matching the DTOs exactly.
2. Create a fetch/axios wrapper function per endpoint (attach `Authorization: Bearer <token>` from the auth store, except for the endpoints marked public).
3. Handle the standard error shape (ErrorResponse) and surface `message` to the UI.
4. For paginated endpoints, handle Spring's Page<T> response shape ({content, totalElements, totalPages, number, size, ...}).
5. Wire up React Query (or whatever data layer this project uses) hooks for each endpoint, with sensible query keys and cache invalidation on mutations (e.g. creating a Feature invalidates the Features-by-project list).

Start with Auth (register/login/logout) and Project CRUD, then Feature, TestCase, TestSuite/run flow, TestEvidence, ApiKey, and ActivityLog, in that order.
```

---

## 0. Base setup

- **Base URL (local dev):** `http://localhost:8080/api/v1`
- **CORS:** allowed origins default to `http://localhost:5173,http://127.0.0.1:5173` (Vite default) — configurable via `CORS_ALLOWED_ORIGINS` env var on the backend. Custom response headers (currently just `X-Generation-Warnings-Count`, see §5e) must be added to `exposedHeaders` on the backend before frontend JS can read them cross-origin — ask the backend to add any new one you need.
- **Auth:** JWT bearer token. After login/register, store `token` and send `Authorization: Bearer <token>` on every subsequent request except the public ones below.
- **Alternate auth:** API Keys (`ApiKeyController`) are validated by the same filter chain but are meant for external tool integration (Katalon/Jenkins), not the frontend — send as `Authorization: Bearer <rawKey>` too if ever needed, but normally the FE uses JWT.
- **Content type:** `application/json` for everything except file upload (`multipart/form-data`) and the binary download/export endpoints (`.xlsx`, `.zip`).
- **Rate limiting:** `/auth/authenticate` and `/auth/register` are limited to 10 attempts/minute per IP by default — a 429 response uses the same `ErrorResponse` shape.

### Public endpoints (no token required)
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/authenticate`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /swagger-ui.html`, `/v3/api-docs/**` (interactive docs, live while backend is running)
- `GET /actuator/health`, `/actuator/info`

Everything else requires a valid, non-blacklisted JWT (`isAuthenticated()`), and some endpoints additionally require specific roles (noted per endpoint below).

### Roles (`Role` enum)
`ADMIN`, `TESTER`, `DEVELOPER`, `AUTOMATION` — set at registration, used in `@PreAuthorize("hasRole(...)")` / `hasAnyRole(...)` checks. Project-level permissions (below) are a *separate* system layered on top.

### Project member roles (string field, not the same enum as above)
`ADMIN`, `TESTER`, `DEVELOPER`, `VIEWER` — set per user per project via `ProjectMemberRequest.role`. `OWNER` is **not** an assignable value — it's synthesized automatically for whoever created the project (`Project.createdBy`) and shows up in member list responses, but the backend rejects it if sent in a request. Internally these map to a `PermissionLevel` used for access checks: `ADMIN` → `ADMIN`, `TESTER`/`DEVELOPER` → `CAN_EDIT`, `VIEWER` → `CAN_VIEW`, plus the synthetic `OWNER` level for the creator.
- **View access** (`isViewAccessAllowed`): any project member, any level.
- **Edit access** (`isEditAccessAllowed`): `OWNER`, `ADMIN`, or `CAN_EDIT` (i.e. project role `ADMIN`/`TESTER`/`DEVELOPER`).
- **Delete/manage-members access** (`isDeleteAccessAllowed`): `OWNER` or `ADMIN` only.

### Standard error shape (`ErrorResponse`)
```json
{
  "timestamp": "2026-08-27T10:15:00",
  "status": 404,
  "error": "Not Found",
  "message": "Project not found with id: 5",
  "path": "/api/v1/project/5"
}
```
Validation errors (`@Valid` failures, HTTP 400) instead return a flat map: `{ "<fieldName>": "<message>", ... }`.

### Pagination (Spring `Page<T>`)
Endpoints marked **[paginated]** accept `?page=0&size=20&sort=fieldName,asc|desc` query params and return:
```json
{
  "content": [ /* array of items */ ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

---

## 1. Auth — `/api/v1/auth`

### `POST /auth/register` — public
Request:
```ts
{ username: string; email: string; name: string; password: string; role?: "ADMIN"|"TESTER"|"DEVELOPER"|"AUTOMATION" }
```
Response `200`: `AuthenticationResponse`
```ts
{ userId: string; username: string; role: string; token: string; message: string }
```

### `POST /auth/authenticate` — public (login)
Request: `{ username: string; password: string }`
Response `200`: same `AuthenticationResponse` shape as register.
Errors: `401` (bad credentials), `429` (rate limited).

### `POST /auth/forgot-password` — public
Request: `{ email: string }` (must be a valid email)
Response `200`: `{ message: string }` — **always** the same generic message whether or not the email exists (prevents user enumeration).

### `POST /auth/reset-password` — public
Request: `{ token: string; newPassword: string }` (newPassword min 8 chars)
Response `200`: `{ message: string }`

### `POST /auth/logout` — requires auth
Header: `Authorization: Bearer <token>` (the token itself is what gets blacklisted)
Response `200`: `{ message: string }`. The same JWT is rejected (`401`) on any request after this, even though it hasn't naturally expired yet.

Google OAuth2 (`/oauth2/authorization/google` redirect flow, backend-driven) is also wired up; the frontend only needs to handle the final redirect back with a token in the query string (`OAuth2RedirectPage`) — see the frontend repo for the exact contract, it isn't a plain REST endpoint.

---

## 2. Projects — `/api/v1/project`

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/project` | any authenticated user | **[paginated]**, default sort `createdAt,desc`. Returns only projects the caller owns or is a member of. |
| GET | `/project/{id}` | authenticated + must be a project member | 403 if not a member |
| POST | `/project` | role `ADMIN` or `TESTER` | creates project |
| PUT | `/project/{id}` | role `ADMIN` or `TESTER` | |
| DELETE | `/project/{id}` | role `ADMIN` or `TESTER` | 204 No Content |

**`ProjectRequest`** (create/update body):
```ts
{ name: string; description?: string; type: string; status?: string } // name required (max 255), type required
```
**`ProjectResponse`**:
```ts
{ id: number; name: string; description: string; type: string; status: string; createdByUsername: string; createdAt: string; updatedAt: string }
```

---

## 3. Project Members — `/api/v1/project/{projectId}/members`

| Method | Path | Auth (checked in service layer) |
|---|---|---|
| POST | `/project/{projectId}/members` | caller must be `OWNER` or `ADMIN` of the project |
| GET | `/project/{projectId}/members` | caller must have view access (any member) |
| PUT | `/project/{projectId}/members/{userId}` | caller must be `OWNER` or `ADMIN` |
| DELETE | `/project/{projectId}/members/{userId}` | caller must be `OWNER` or `ADMIN` |

**`ProjectMemberRequest`**:
```ts
{ idUser: number; role: "ADMIN"|"TESTER"|"DEVELOPER"|"VIEWER" } // "OWNER" is rejected — never send it
```
**`ProjectMemberResponse`**:
```ts
{ id: number | null; idProject: number; idUser: number; username: string; email: string; role: "OWNER"|"ADMIN"|"TESTER"|"DEVELOPER"|"VIEWER"; joinedAt: string }
```
`id` is `null` for the synthetic `OWNER` row (the project creator, not a real `project_members` table row) — that row can't be edited or removed via this API. POST/PUT return `201`/`200`; DELETE returns `204`. Adding a member fires a `PROJECT_MEMBER_ADDED` in-app notification to them (see §13).

---

## 4. Features — `/api/v1/feature`

| Method | Path | Auth |
|---|---|---|
| GET | `/feature/project/{projectId}` | authenticated + project view access |
| GET | `/feature/{featureId}` | authenticated + project view access |
| POST | `/feature` | authenticated + project edit access (checked in service) |
| PUT | `/feature/{featureId}` | authenticated + project edit access |
| DELETE | `/feature/{featureId}` | authenticated + project edit access |

**`FeatureRequest`**:
```ts
{ idProject: number; name: string; type?: string; description?: string; tag?: string; status?: string }
```
**`FeatureResponse`**:
```ts
{ id: number; idProject: number; name: string; description: string; type: string; tag: string; status: string; createdBy: number; createdByUsername: string; createdAt: string; updatedAt: string }
```
List endpoint returns a plain array (not paginated). Create returns `201`.

---

## 5. Test Cases — `/api/v1/testcase`

| Method | Path | Auth |
|---|---|---|
| GET | `/testcase/project/{projectId}` | authenticated + view access — **[paginated]** |
| GET | `/testcase/feature/{featureId}` | authenticated + view access — **[paginated]** |
| GET | `/testcase/{id}` | authenticated + view access |
| POST | `/testcase` | role `ADMIN` or `TESTER` |
| PUT | `/testcase/{id}` | role `ADMIN` or `TESTER` |
| DELETE | `/testcase/{id}` | role `ADMIN` or `TESTER` |

**`TestCaseRequest`**:
```ts
{
  idFeature: number; idProject: number;
  name: string;                 // required, max 255
  description?: string;
  type: string;                 // required
  tag?: string;
  preCondition?: string;
  testSteps: string;             // required
  testData?: string;
  postCondition?: string;
  expectedResult: string;        // required
}
```
**`TestCaseResponse`**: all `TestCaseRequest` fields plus `id, createdBy, createdByUsername, createdAt, updatedAt`.

### 5a. Bulk actions — `/testcase/bulk-*`

Each ID in a batch is processed **independently** — one that's not found, or that the caller lacks permission for, doesn't fail the rest. Always returns `200` with a summary.

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/testcase/bulk-delete` | role `ADMIN`, `TESTER`, or `DEVELOPER` | `{ ids: number[] }` |
| PUT | `/testcase/bulk-tag` | role `ADMIN`, `TESTER`, or `DEVELOPER` | `{ ids: number[]; tag: string \| null }` (null/empty clears the tag) |
| PUT | `/testcase/bulk-move` | role `ADMIN`, `TESTER`, or `DEVELOPER` | `{ ids: number[]; targetFeatureId: number }` |

**`BulkOperationResponse`** (all three return this shape):
```ts
{ totalRequested: number; successCount: number; failedCount: number; errors: { id: number; message: string }[] }
```

### 5b. Import Test Cases from CSV/Excel — `/testcase/feature/{featureId}/import`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/testcase/feature/{featureId}/import` | role `ADMIN` or `TESTER` | `multipart/form-data`: field `file` (`.csv`/`.xlsx`/`.xls`) |
| GET | `/testcase/import/template` | authenticated | returns a ready-to-fill `.xlsx` template |

All rows import into the one Feature named in the path. A row failing validation (missing name/type/steps/expected-result, or an unrecognized `type`) is skipped and reported — it never fails the rest of the file.

**`TestCaseImportResponse`**:
```ts
{ totalRows: number; importedCount: number; failedCount: number; errors: { rowNumber: number; testCaseName: string | null; message: string }[] }
```
Column headers accept ID or EN variants (e.g. "Nama Test Case" / "Name" both map to `name`); required columns: Name, Type, Test Steps, Expected Result.

### 5c. Generate Test Cases from a requirement file (Gherkin) — `/testcase/project/{projectId}/generate-from-requirements`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/testcase/project/{projectId}/generate-from-requirements` | role `ADMIN` or `TESTER` | `multipart/form-data`: field `file` (`.csv`/`.xlsx`/`.xls`) |
| GET | `/testcase/generate-from-requirements/template` | authenticated | ready-to-fill `.xlsx` template with a worked example |

**This is a deterministic transformation, not AI.** Required columns: `Module Name`, `Scenario Name`, `Acceptance Criteria (Gherkin)` (a `Given`/`When`/`Then`/`And`/`But` block in one cell). Optional: `Feature/User Story ID`, `Pre-conditions`, `Input Fields & Validation Rules`, `Priority`.
- `Given` lines → `preCondition` (combined with the Pre-conditions column). `When` lines → `testSteps` (numbered). `Then` lines → `expectedResult` (numbered). `And`/`But` follow whichever section is currently active.
- A row missing a `When` or a `Then` line is rejected (those columns are `NOT NULL`) and reported, not silently dropped or half-generated.
- `Module Name` maps to a Feature in the project by name (case-insensitive); one that doesn't exist yet is auto-created.
- `Feature/User Story ID` + `Priority` combine into the generated Test Case's `tag` (e.g. `"US-101 | P1"`).

**`RequirementImportResponse`**:
```ts
{ totalRows: number; generatedCount: number; failedCount: number; featuresCreatedCount: number; errors: { rowNumber: number; testCaseName: string | null; message: string }[] }
```

### 5d. Generate an automation script (Playwright TypeScript, Page Object Model) — `/testcase/project/{projectId}/generate-automation-script`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/testcase/project/{projectId}/generate-automation-script` | role `ADMIN`, `TESTER`, or `DEVELOPER` | `multipart/form-data`: field `file`. Response is a **binary `.zip`**, not JSON. |
| GET | `/testcase/generate-automation-script/template` | authenticated | ready-to-fill `.xlsx` template with a worked 5-step example |

Required columns: `Module Name`, `Scenario Name`, `Field Name`, `Element Locator`, `Action` (one of `click`, `fill`, `select`, `check`, `uncheck`, `assertText`, `assertVisible`, `navigate` — case-insensitive, common aliases like `type`/`goto` also accepted). Optional: `Step Order` (defaults to file row order per scenario), `Input Data` (the value for `fill`/`select`/`assertText`, or the URL for `navigate`). `Element Locator` may be blank **only** for `navigate` rows.

This is a fully deterministic, stateless transformation — nothing is read from or written to the database besides the permission check on the Project. Rows with an unrecognized action, a missing locator, or a locator conflicting with an already-registered method of the same name are skipped and listed inside the zip (see below) rather than failing the whole file — unless *every* row fails, in which case it's a normal `400`.

The zip contains:
- `pages/<Module>Page.ts` — one Page Object class per distinct `Module Name`, deduplicated by (action, field) across all scenarios that reference it.
- `tests/<Scenario>.spec.ts` — one spec file per distinct `Scenario Name`, steps ordered by `Step Order`, importing and instantiating whichever Page Object(s) it uses.
- `README_WARNINGS.txt` — only present if any rows were skipped; lists each skipped row number and why.

The response also carries an **`X-Generation-Warnings-Count`** header (an integer) so the frontend can show "N rows skipped, check README_WARNINGS.txt" without unzipping the file itself; it's already in the backend's CORS `exposedHeaders`.

**Only Playwright (TypeScript) is implemented.** Robot Framework and Selenium (Java) are not available yet.

---

## 6. Test Suites (runs) — `/api/v1/testsuite`

This is the "record a test execution" flow: one `TestSuite` (a run) contains many `TestSuiteRunDetail` rows (one per test case executed in that run).

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/testsuite/run` | role `ADMIN`, `TESTER`, or `DEVELOPER` | Creates a whole run + its details in one call (monolithic create) |
| GET | `/testsuite/{id}` | authenticated + view access | |
| GET | `/testsuite/project/{projectId}` | authenticated + view access — **[paginated]** | |
| PUT | `/testsuite/{id}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | full metadata update |
| PUT | `/testsuite/{id}/finalize` | role `ADMIN`, `TESTER`, or `DEVELOPER` | sets status totals / end date; also triggers deploy-readiness notifications/email (see §13) |
| GET | `/testsuite/{id}/deploy-decision` | authenticated + view access | pass-rate vs threshold (default 95%, `DEPLOY_PASS_RATE_THRESHOLD` env var) |
| GET | `/testsuite/{id}/export/excel` | authenticated + view access | returns `.xlsx` binary, `Content-Disposition: attachment` |
| DELETE | `/testsuite/{id}` | role `ADMIN` or `TESTER` | |
| POST | `/testsuite/{suiteId}/detail` | role `ADMIN`, `TESTER`, or `DEVELOPER` | add one more detail row to an existing run |
| GET | `/testsuite/detail/{detailId}` | authenticated + view access | |
| PUT | `/testsuite/detail/{detailId}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | |
| DELETE | `/testsuite/detail/{detailId}` | role `ADMIN` or `TESTER` | |

> ⚠️ Note: these `/testsuite/**` endpoints return errors as **plain strings in the body**, not the standard `ErrorResponse` shape, for the `403`/`404` cases specifically (caught inside the controller). Other errors (validation, 500) still use the standard shapes above. **Exception:** `POST /testsuite/import/junit` (§6a) deliberately bypasses that wrapper and always returns the standard `ErrorResponse`/validation-map shapes, because the plain-string wrapper mishandles `400`s.

**`TestSuiteRequest`** (POST `/testsuite/run` body):
```ts
{
  projectId: number;             // required
  name: string;                  // required
  description?: string;
  tag?: string;
  testStage: string;             // required
  testEnvironment: string;       // required
  hostname?: string; os?: string; version?: string; browser?: string;
  statusTotalPassed?: number; statusTotalFailed?: number; statusTotalError?: number; statusTotalSkipped?: number;
  startDate: string;              // required, ISO datetime
  endDate?: string;
  elapsedTime: number;             // required, ms
  executionType: string;           // required, e.g. "MANUAL" | "AUTOMATION"
  runDetails: TestSuiteRunDetailRequest[]; // required, at least the array must be present
}
```
**`TestSuiteRunDetailRequest`** (used both nested in the above, and standalone for `/testsuite/{suiteId}/detail`):
```ts
{
  idTestCase: number;             // required
  status: "PASSED"|"FAILED"|"ERROR"|"SKIPPED"; // required
  actualResult?: string;
  remarks?: string;
  startDate: string;               // required, ISO datetime
  endDate?: string;
  elapsedTime?: number;             // ms
}
```
**`TestSuiteResponse`**:
```ts
{
  id: number; projectId: number; projectName: string;
  name: string; description: string; tag: string;
  testStage: string; testEnvironment: string; executionType: string;
  hostname: string; os: string; version: string; browser: string;
  statusTotalPassed: number; statusTotalFailed: number; statusTotalError: number; statusTotalSkipped: number;
  startDate: string; endDate: string; elapsedTime: number;
  executedById: number; executedByUsername: string;
  createdById: number; createdByUsername: string;
  createdAt: string; updatedAt: string;
  runDetails: TestSuiteRunDetailResponse[];
}
```
**`TestSuiteRunDetailResponse`**:
```ts
{ id: number; idTestSuite: number; idTestCase: number; testCaseName: string; status: string; actualResult: string; remarks: string; startDate: string; endDate: string; elapsedTime: number; executedById: number; executedByUsername: string }
```
**`DeployDecisionResponse`** (GET `/testsuite/{id}/deploy-decision`):
```ts
{
  testSuiteId: number; testSuiteName: string;
  totalPassed: number; totalFailed: number; totalError: number; totalSkipped: number; totalTests: number;
  passRatePercent: number; thresholdPercent: number;
  deployRecommended: boolean;
  decision: "LAYAK_DEPLOY" | "TIDAK_LAYAK_DEPLOY";
  reason: string;
}
```

### 6a. Import a JUnit XML report (CI/CD) — `POST /testsuite/import/junit`

`multipart/form-data`, role `ADMIN`, `TESTER`, or `DEVELOPER`. Fields:
```ts
{
  projectId: number;             // required, query/form param
  defaultFeatureId: number;      // required — fallback Feature for <testcase> names that don't match an existing Test Case
  testSuiteName?: string;         // optional, defaults to "CI Import - <timestamp>"
  testStage?: string;             // defaults to "STAGING"
  testEnvironment?: string;       // defaults to "CI/CD"
  tag?: string;
  file: File;                     // the JUnit XML report
}
```
Every `<testcase>` in the file (regardless of `<testsuite>`/`<testsuites>` nesting) becomes one `TestSuiteRunDetail`, matched to an existing Test Case by name (case-insensitive, project-wide) or auto-created in `defaultFeatureId` (tagged `"ci-import"`) if no match exists. Status comes from child elements: `<error>` → `ERROR`, `<failure>` → `FAILED`, `<skipped>` → `SKIPPED`, none → `PASSED`. The resulting run is created **and immediately finalized** (a JUnit report represents a completed execution), so it triggers the same deploy-readiness notifications/email as a manual finalize (§13). Parsing rejects DOCTYPE declarations and external entities (XXE-hardened).

**`JUnitImportResponse`**:
```ts
{
  testSuiteId: number; testSuiteName: string;
  totalTestCases: number; matchedExistingCount: number; autoCreatedCount: number;
  totalPassed: number; totalFailed: number; totalError: number; totalSkipped: number;
  warnings: string[];
}
```

### 6b. Legacy low-level detail endpoints — `/api/v1/run-details` (avoid using from FE)
These operate directly on the `TestSuiteRunDetail` entity (not the DTO) and largely duplicate section 6. `GET /run-details` (list all, cross-project) is **ADMIN-only**. Prefer the `/testsuite/**` endpoints above for anything the frontend builds; this section exists mainly for backward compatibility / admin tooling.

---

## 7. Test Evidence — `/api/v1/evidence`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/evidence` | authenticated + edit access | metadata-only, for files already sitting in external storage (S3/GCS) — no upload |
| POST | `/evidence/upload` | authenticated + edit access | `multipart/form-data`: fields `runDetailId` (number), `file` (binary), `description` (optional string) |
| GET | `/evidence/{evidenceId}/download` | authenticated + view access | binary response, only works for evidence created via `/upload` |
| GET | `/evidence/run/{runDetailId}` | authenticated + view access | list all evidence for one run detail |

**`TestEvidenceRequest`** (metadata-only POST):
```ts
{ runDetailId: number; fileName?: string; fileType?: string; fileSize?: number; storagePathUrl?: string; description?: string }
```
**`TestEvidenceResponse`**:
```ts
{ id: number; runDetailId: number; fileName: string; fileType: string; fileSize: number; storagePathUrl: string; description: string; downloadUrl: string | null } // downloadUrl only set for uploaded files
```
For `/evidence/upload`, build a `FormData` on the frontend:
```ts
const fd = new FormData();
fd.append("runDetailId", String(runDetailId));
fd.append("file", file);
if (description) fd.append("description", description);
// POST with fetch(url, { method: "POST", body: fd, headers: { Authorization: `Bearer ${token}` } })
// do NOT set Content-Type manually — let the browser set the multipart boundary
```

**Size & compression:** uploads are rejected above `app.evidence.max-file-size-mb` (default **10MB**, `EVIDENCE_MAX_FILE_SIZE_MB`) per file, and above `app.evidence.max-total-size-per-run-mb` (default **50MB**, `EVIDENCE_MAX_TOTAL_SIZE_PER_RUN_MB`) summed across all evidence on one `runDetailId`. JPEG/PNG uploads are automatically resized (longest side capped at `app.evidence.image-max-dimension-px`, default 1920px) and JPEG is re-encoded at `app.evidence.image-jpeg-quality` (default 0.82) before being stored — the `fileSize` in the response reflects the size *after* compression, which can be smaller than what the client actually uploaded. Storage is local disk only (`app.evidence.storage-dir`) — there is no S3/cloud backend yet, so `storagePathUrl`-only rows (external files) are the only way to reference evidence hosted elsewhere.

---

## 8. API Keys — `/api/v1/apikey`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/apikey` | role `ADMIN`, `TESTER`, or `DEVELOPER` | response includes `rawKey` — **shown only once**, store the hash server-side only |
| GET | `/apikey` | authenticated | keys for the current user |
| DELETE | `/apikey/{id}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | revoke |

**`ApiKeyRequest`**: `{ name: string; expiresAt?: string }`
**`ApiKeyResponse`**: `{ id: number; idUser: number; name: string; status: string; expiresAt: string; lastUsedAt: string; createdByUsername: string; createdAt: string; rawKey: string | null }`

If building an "API Keys" settings page: show `rawKey` in a copy-once dialog immediately after creation, then never display it again (subsequent `GET /apikey` calls won't include it).

---

## 9. Activity Log — `/api/v1/activity-log`

| Method | Path | Auth |
|---|---|---|
| GET | `/activity-log` | role `ADMIN` only — **[paginated]** |

Returns `Page<ActivityLog>` where each item is:
```ts
{ id: number; idUser: number | null; action: string; entityType: string; entityId: number; details: string; ipAddress: string; createdAt: string }
```
Read-only — no create/update/delete endpoints (writes happen internally in the backend).

---

## 10. Quality Dashboard — `/api/v1/dashboard`

| Method | Path | Auth |
|---|---|---|
| GET | `/dashboard/project/{projectId}` | authenticated + view access |

One aggregate call for a whole dashboard screen: feature-by-feature Test Case coverage (sorted thinnest-first, so gaps surface at the top), a pass-rate trend across finalized runs (capped at the 30 most recent), an overall status breakdown, and the latest deploy decision.

**`ProjectDashboardResponse`**:
```ts
{
  projectId: number; projectName: string;
  totalFeatures: number; totalTestCases: number; totalTestSuiteRuns: number; totalFinalizedRuns: number;
  statusBreakdown: { totalPassed: number; totalFailed: number; totalError: number; totalSkipped: number; totalTests: number; passRatePercent: number };
  featureCoverage: { featureId: number; featureName: string; testCaseCount: number }[];
  passRateTrend: { testSuiteId: number; testSuiteName: string; startDate: string; endDate: string; totalPassed: number; totalFailed: number; totalError: number; totalSkipped: number; totalTests: number; passRatePercent: number }[];
  latestDeployDecision: DeployDecisionResponse | null; // null if no run has been finalized yet
}
```

---

## 11. Requirements Traceability — `/api/v1/traceability`

| Method | Path | Auth |
|---|---|---|
| GET | `/traceability/project/{projectId}` | authenticated + view access |

Feature acts as the "requirement" unit here (every Test Case already belongs to exactly one, so no separate Requirement entity exists) — this returns every Feature with its Test Cases and each one's **latest** execution status across *all* Test Suite Runs in the project. The two things worth building UI around: a Feature with `testCaseCount === 0` (no test coverage at all) and a Test Case with `lastExecutionStatus === null` (has test cases, but none have ever actually been run).

**`TraceabilityMatrixResponse`**:
```ts
{
  projectId: number; projectName: string;
  features: {
    featureId: number; featureName: string;
    testCaseCount: number; executedCount: number; passedCount: number; failedCount: number; notExecutedCount: number;
    coveragePercent: number; // executedCount / testCaseCount * 100
    testCases: {
      testCaseId: number; testCaseName: string; tag: string | null;
      lastExecutionStatus: "PASSED"|"FAILED"|"ERROR"|"SKIPPED" | null; // null = never executed
      lastExecutedAt: string | null; lastTestSuiteId: number | null; lastTestSuiteName: string | null;
    }[];
  }[];
}
```

---

## 12. Global Search — `/api/v1/search`

| Method | Path | Auth |
|---|---|---|
| GET | `/search?q=<query>` | authenticated |

Searches Project/Feature/Test Case/Test Suite Run names (and Test Case tag/description) in one call, up to 8 matches per type, restricted to projects the caller can access. Queries under 2 characters return an empty result without hitting the database.

**`GlobalSearchResponse`**:
```ts
{
  query: string;
  results: {
    type: "PROJECT"|"FEATURE"|"TEST_CASE"|"TEST_SUITE";
    id: number; title: string; subtitle: string | null; link: string; // link is a ready-to-navigate FE path, e.g. "/test-suites/detail/42"
    projectId: number; projectName: string;
  }[];
}
```

---

## 13. Notifications — `/api/v1/notifications`

| Method | Path | Auth |
|---|---|---|
| GET | `/notifications` | authenticated | **[paginated]**, default sort `createdAt,desc` — the current user's own notifications |
| GET | `/notifications/unread-count` | authenticated | `{ count: number }` |
| PUT | `/notifications/{id}/read` | authenticated | marks one as read; 404 if it's not the caller's |
| PUT | `/notifications/read-all` | authenticated | bulk mark-all-as-read for the caller |

**`NotificationResponse`**:
```ts
{
  id: number;
  type: "PROJECT_MEMBER_ADDED"|"TEST_RUN_FINALIZED"|"DEPLOY_NOT_READY"|"BUG_ASSIGNED";
  title: string; message: string; link: string | null; isRead: boolean; createdAt: string;
}
```
Triggers, for reference (all fire automatically, nothing the frontend needs to call):
- `PROJECT_MEMBER_ADDED` → the newly-added member, when `POST /project/{id}/members` succeeds.
- `TEST_RUN_FINALIZED` → the run's executor, on `PUT /testsuite/{id}/finalize` (and on `POST /testsuite/import/junit`, which finalizes automatically).
- `DEPLOY_NOT_READY` → the project's OWNER + ADMIN members, when the same finalize evaluates to `TIDAK_LAYAK_DEPLOY`. The same finalize also sends an HTML deploy-readiness email to the same recipient set (best-effort — an SMTP failure is logged, never blocks the finalize).
- `BUG_ASSIGNED` → whoever a bug gets assigned to, via §14.

No SMTP/notification config to worry about from the frontend — just poll or invalidate `/notifications/unread-count` after actions that might generate one, and render a bell/dropdown off the paginated list.

---

## 14. Bugs — `/api/v1/bugs`

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/bugs` | role `ADMIN`, `TESTER`, or `DEVELOPER` | |
| GET | `/bugs/project/{projectId}` | authenticated + view access | **[paginated]**; optional filters `?status=&severity=&assignedToUserId=` |
| GET | `/bugs/{id}` | authenticated + view access | |
| PUT | `/bugs/{id}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | updates title/description/severity/Test Case & execution links only — **not** status or assignee, those have their own endpoints below |
| PUT | `/bugs/{id}/status` | role `ADMIN`, `TESTER`, or `DEVELOPER` | body `{ status: BugStatus }` — validated against the lifecycle transition map, `400` if not a legal next state |
| PUT | `/bugs/{id}/assign` | role `ADMIN`, `TESTER`, or `DEVELOPER` | body `{ assignedToUserId: number \| null }` (`null` unassigns); fires `BUG_ASSIGNED` notification when non-null |
| DELETE | `/bugs/{id}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | requires `OWNER`/`ADMIN`-level project access (`isDeleteAccessAllowed`), stricter than the other bug endpoints |

**`BugStatus`** (10-state lifecycle): `NEW → IN_ANALYSIS → READY_FOR_DEVELOPMENT → IN_DEVELOPMENT → READY_FOR_TESTING → IN_TESTING → READY_FOR_UAT → IN_UAT → READY_FOR_DEPLOYMENT → DEPLOYED`. Allowed transitions are forward-only along that chain, plus two pragmatic backward paths: `IN_TESTING`/`IN_UAT` → `IN_DEVELOPMENT` (failed a gate, needs rework) and `DEPLOYED` → `IN_ANALYSIS` (reopen for a post-release regression). Fetch the current status from the bug and only offer the legal next value(s) in the UI — a request for any other transition is rejected with `400`.

**`BugSeverity`**: `LOW`|`MEDIUM`|`HIGH`|`CRITICAL`.

**`BugRequest`** (create/update body):
```ts
{
  projectId: number;              // required
  testCaseId?: number | null;      // optional — must belong to the same project
  testSuiteRunDetailId?: number | null; // optional — the specific execution that surfaced it; must belong to the same project
  title: string;                   // required
  description?: string;
  severity: BugSeverity;           // required
  assignedToUserId?: number | null; // only honored on CREATE; use PUT .../assign to change it later
}
```
**`BugResponse`**:
```ts
{
  id: number; projectId: number; projectName: string;
  testCaseId: number | null; testCaseName: string | null;
  testSuiteRunDetailId: number | null; testSuiteId: number | null; testSuiteName: string | null;
  title: string; description: string | null; severity: BugSeverity; status: BugStatus;
  reportedById: number; reportedByUsername: string;
  assignedToId: number | null; assignedToUsername: string | null;
  createdAt: string; updatedAt: string;
}
```

---

## Quick reference: which calls need which role

| Action | Requirement |
|---|---|
| Read anything (project/feature/testcase/testsuite/evidence/bugs/dashboard/traceability) | just be a member of the project |
| Create/edit/delete Project | global role `ADMIN` or `TESTER` |
| Create/edit/delete TestCase, bulk actions, import, generate-from-requirements | global role `ADMIN` or `TESTER` |
| Create/update TestSuite run, JUnit import, generate-automation-script | global role `ADMIN`, `TESTER`, or `DEVELOPER` |
| Delete TestSuite / detail | global role `ADMIN` or `TESTER` |
| Create/edit Feature | project membership with edit access (any global role) |
| Manage Project Members | project role `OWNER` or `ADMIN` |
| Create/revoke ApiKey | global role `ADMIN`, `TESTER`, or `DEVELOPER` |
| View Activity Log | global role `ADMIN` |
| Create/edit/assign/status-change Bug | global role `ADMIN`, `TESTER`, or `DEVELOPER` |
| Delete Bug | same as above, but project role `OWNER`/`ADMIN` specifically |
| Notifications (own) | any authenticated user |
| Global Search | any authenticated user (results scoped to accessible projects) |

---

*Last regenerated after the Notifications / Bug Tracking / Bulk Actions / Global Search / Requirements Traceability / Evidence quotas & compression / CI-CD JUnit import / Requirement-to-TestCase generation / Automation script generation features were added. If the backend changes again, regenerate this from the controllers/DTOs, or better: hit the live `/v3/api-docs` (Swagger/OpenAPI JSON) for the always-current machine-readable spec while the backend is running.*
