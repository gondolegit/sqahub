# SQAHub Backend — API Spec for Frontend Implementation

> **How to use this file:** paste this whole document into Claude Code inside the `sqahub-fe` project and ask it to implement API client functions / hooks / pages for these endpoints. Everything below is derived directly from the backend source code (`sqahub` repo) as of commit on the `main` branch — controllers, DTOs, and `SecurityConfiguration`.

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
- **CORS:** allowed origins default to `http://localhost:5173,http://127.0.0.1:5173` (Vite default) — configurable via `CORS_ALLOWED_ORIGINS` env var on the backend. If your FE dev server runs elsewhere, ask the backend to add it.
- **Auth:** JWT bearer token. After login/register, store `token` and send `Authorization: Bearer <token>` on every subsequent request except the public ones below.
- **Alternate auth:** API Keys (`ApiKeyController`) are validated by the same filter chain but are meant for external tool integration (Katalon/Jenkins), not the frontend — send as `Authorization: Bearer <rawKey>` too if ever needed, but normally the FE uses JWT.
- **Content type:** `application/json` for everything except file upload (`multipart/form-data`) and the two binary download endpoints.
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
`MANAGER`, `TESTER`, `DEVELOPER`, `VIEWER` — set per user per project via `ProjectMemberRequest.role`. Endpoints not gated by a global `@PreAuthorize` role (e.g. Feature/TestCase reads, TestSuite run creation) instead check project membership + role in the service layer and throw `403` if the caller isn't a member with sufficient access.

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
| POST | `/project/{projectId}/members` | caller must be MANAGER of the project |
| GET | `/project/{projectId}/members` | caller must have view access |
| PUT | `/project/{projectId}/members/{userId}` | caller must be MANAGER |
| DELETE | `/project/{projectId}/members/{userId}` | caller must be MANAGER |

**`ProjectMemberRequest`**:
```ts
{ idUser: number; role: "MANAGER"|"TESTER"|"DEVELOPER"|"VIEWER" }
```
**`ProjectMemberResponse`**:
```ts
{ id: number; idProject: number; idUser: number; username: string; email: string; role: string; joinedAt: string }
```
POST/PUT return `201`/`200`; DELETE returns `204`.

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

---

## 6. Test Suites (runs) — `/api/v1/testsuite`

This is the "record a test execution" flow: one `TestSuite` (a run) contains many `TestSuiteRunDetail` rows (one per test case executed in that run).

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/testsuite/run` | role `ADMIN`, `TESTER`, or `DEVELOPER` | Creates a whole run + its details in one call (monolithic create) |
| GET | `/testsuite/{id}` | authenticated + view access | |
| GET | `/testsuite/project/{projectId}` | authenticated + view access — **[paginated]** | |
| PUT | `/testsuite/{id}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | full metadata update |
| PUT | `/testsuite/{id}/finalize` | role `ADMIN`, `TESTER`, or `DEVELOPER` | sets status totals / end date |
| GET | `/testsuite/{id}/deploy-decision` | authenticated + view access | pass-rate vs threshold (default 95%, `DEPLOY_PASS_RATE_THRESHOLD` env var) |
| GET | `/testsuite/{id}/export/excel` | authenticated + view access | returns `.xlsx` binary, `Content-Disposition: attachment` |
| DELETE | `/testsuite/{id}` | role `ADMIN` or `TESTER` | |
| POST | `/testsuite/{suiteId}/detail` | role `ADMIN`, `TESTER`, or `DEVELOPER` | add one more detail row to an existing run |
| GET | `/testsuite/detail/{detailId}` | authenticated + view access | |
| PUT | `/testsuite/detail/{detailId}` | role `ADMIN`, `TESTER`, or `DEVELOPER` | |
| DELETE | `/testsuite/detail/{detailId}` | role `ADMIN` or `TESTER` | |

> ⚠️ Note: these `/testsuite/**` endpoints return errors as **plain strings in the body**, not the standard `ErrorResponse` shape, for the `403`/`404` cases specifically (caught inside the controller). Other errors (validation, 500) still use the standard shapes above.

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
  executionType: string;           // required, e.g. "MANUAL" | "AUTOMATED"
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

## Quick reference: which calls need which role

| Action | Requirement |
|---|---|
| Read anything (project/feature/testcase/testsuite/evidence) | just be a member of the project |
| Create/edit/delete Project | global role `ADMIN` or `TESTER` |
| Create/edit/delete TestCase | global role `ADMIN` or `TESTER` |
| Create/update TestSuite run | global role `ADMIN`, `TESTER`, or `DEVELOPER` |
| Delete TestSuite / detail | global role `ADMIN` or `TESTER` |
| Create/edit Feature | project membership with edit access (any global role) |
| Manage Project Members | project role `MANAGER` |
| Create/revoke ApiKey | global role `ADMIN`, `TESTER`, or `DEVELOPER` |
| View Activity Log | global role `ADMIN` |

---

*Generated from the backend source on 2026-08-27. If the backend changes, regenerate this from the controllers/DTOs, or better: hit the live `/v3/api-docs` (Swagger/OpenAPI JSON) for the always-current machine-readable spec while the backend is running.*
