# Member daily tasks — Slice 3A

Backend storage and targeted save APIs for **오늘의 할일**. This slice does not add a Home card, calendar UI, or browser draft storage. Those follow in 3B/3C.

## Boundaries

- Personal tasks belong only to the authenticated ordinary `MEMBER`. Every service entry point checks the current database role; `ADMIN` and `STAFF` receive 403, including their own `/me` calls. There is no manager/target-member endpoint.
- Tasks belong to an explicit date, not a 교시, branch, or shared checklist. Changing a member's branch does not move or expose their tasks.
- Existing weekly/shared plans, branch-wide staff todos, attendance, QR, study totals, and break sessions are untouched. Completing a task never records study time.
- Old plans are not migrated, copied, or interpreted as daily tasks.

## Storage

`member_task_days` stores one revision per `(member_id, task_date)`. Reading an empty date returns revision 0 and creates no row. An emptied date retains its revision.

`member_task_items` stores content, completion, stable order, and a member-scoped create UUID/fingerprint. Deletion hides an item but keeps its create receipt in the same row; it cannot be resurrected by replaying an interrupted add. These tombstones are not a user-facing trash/restore feature. They are excluded from every day/calendar response and are not processed by the staff-todo completion cleanup.

Account/preregistration deletion hard-removes that member's task items (including tombstones) and day revisions through the existing centralized cleanup. Other members' tasks remain intact.

## API

All endpoints require the existing bearer authentication. IDs are derived from the authenticated member, never an owner or branch supplied by the client. Successful writes return HTTP 200 with the complete current day snapshot.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/member-tasks/me?date=2026-10-26` | Current tasks and revision for one date |
| GET | `/api/member-tasks/me/calendar?month=2026-10` | Counts for every date in one month, including zero-count dates |
| POST | `/api/member-tasks/me/items` | Add one task |
| PATCH | `/api/member-tasks/me/items/{itemId}` | Edit content and/or completion |
| DELETE | `/api/member-tasks/me/items/{itemId}?date=2026-10-26&expectedRevision=2` | Delete one task |

Create body:

```json
{
  "date": "2026-10-26",
  "expectedRevision": 0,
  "content": "오답 노트 복습하기",
  "clientRequestId": "f8d0365a-86ef-4f29-a7e1-5665dc4f3fd5"
}
```

Update body (either `content` or `completed`, or both):

```json
{
  "date": "2026-10-26",
  "expectedRevision": 1,
  "completed": true
}
```

Day response:

```json
{
  "memberId": 12,
  "date": "2026-10-26",
  "revision": 2,
  "items": [
    { "id": 21, "content": "오답 노트 복습하기", "completed": true, "sortOrder": 0 }
  ]
}
```

Calendar response contains `memberId`, `month`, and ordered `days` with `date`, `totalCount`, and `completedCount`. It contains no task content, create tokens, fingerprints, tombstones, or other members' information. Counts include only active tasks; completed tasks remain visible until explicitly deleted.

Validation:

- Explicit valid dates in years 0001–9999; canonical `YYYY-MM` calendar parameter. The future UI should derive today's default from Asia/Seoul, not the browser's local timezone. The API does not silently replace a requested date with today.
- Trimmed, nonblank task content, at most 2,000 Java UTF-16 code units; internal line breaks are preserved. PATCH with only `completed` leaves content unchanged. There is no implicit date move or reorder.
- Required nonnegative `expectedRevision`, bounded to JavaScript's safe integer range; invalid references/body values return 400.
- Missing/deleted items, other members' item IDs, and wrong-date item IDs return the same 404 (after validating the caller's current day revision). Stale revisions and changed-payload token reuse return 409. No writes occur on these errors.

## Save safety and future frontend contract

1. Reads use a repeatable-read transaction so revision and items describe one snapshot.
2. Writes lock the authenticated member before checking their current role and the day revision. Concurrent first saves cannot create two revision rows. This is the same member lock held during account deletion.
3. Every accepted mutation advances that date's revision. Other dates and every other domain are untouched. Even a no-op explicit PATCH consumes a revision.
4. Generate one UUID for one logical add. Persist the draft, original payload, and UUID **before** sending it. Retries must reuse that payload/UUID, not generate a new one.
5. Matching create replay is checked before the expected revision and returns the **current** snapshot without another write, even after edits/deletion. Tokens are unique per member across all dates. Reusing a token for different normalized date/content returns 409. The expected revision is not part of its fingerprint.
6. On PATCH/DELETE lost responses or 409, retain the draft, fetch the latest date, and compare the specific item. If already applied, reconcile; otherwise ask for an explicit reviewed retry with the new revision. Never automatically rebase a stale edit or replace the whole day's list.
7. Guard responses/cache keys/drafts by signed-in member and date. Switching dates or routes must not discard unsaved input. Do not confuse “request sent” with “saved.”
8. After successful writes refresh both that date and its calendar month counts. A stale background response must not replace a newer day revision.

Backend conflict protection does not itself persist browser drafts. Slice 3B must implement the above client-side behavior before enabling editing.

## Database rollout

`backend/db/migrations/20261026_02_member_daily_tasks.sql` is an additive PostgreSQL script for the two new tables, constraints, and indexes. It does not alter/backfill existing tables. The script is provided for the user's deployment workflow; it is not an automatically run migration and was not applied to an application or production database during implementation.

Preserve the current data backup and review/apply the schema through the normal user-owned deployment process. Roll out compatible backend/schema before the later frontend editor. Application rollback should leave these new tables intact so personal tasks and retry receipts are not lost.

## Implementation verification

- `cd backend && ./gradlew test bootJar`: successful; 934 tests discovered, 931 passed, 3 optional existing beverage PostgreSQL migration tests skipped because no disposable PostgreSQL URL was configured.
- The 21 new daily-task integration tests use an isolated in-memory H2 database and exercise authenticated HTTP requests, actual JPA transactions, concurrent saves, private ownership, idempotent replay, calendar/date boundaries, and unchanged study/break totals. Existing member-deletion tests now include the new tables and rollback/other-owner preservation.
- `git diff --check`: passed. Frontend worktree unchanged; no frontend tests added or run in this slice.
- PostgreSQL migration execution, production/database validation, UI/draft behavior, and deployment remain unverified. No application/production migration, Git commit, or deployment was performed.
