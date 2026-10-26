# Shared plan checklists: backend foundation

This foundation adds storage and item-level save safety for the three daily checklists. Slice 2A adds targeted weekly-goal saves and lunch/dinner plan creation. Frontend application code, study-time calculations, QR/presence, break-study controls, attendance, and permissions remain unchanged. The inline editor and its API wiring are a separate slice.

## Storage and preservation

| Group value | Display label | Existing zero-based period indexes |
| --- | --- | --- |
| `PERIODS_1_2` | 1–2교시 | 0, 1 |
| `PERIODS_3_5` | 3–5교시 | 2, 3, 4 |
| `PERIODS_6_7` | 6–7교시 | 5, 6 |

- New checklist items live once in `shared_plan_items`, identified by member, week, day, and group. They are not assigned to a pretend single period or copied into every period.
- Existing `weekly_plan_items` are not migrated, merged, deduplicated, or rewritten on read. Regular items appear in the corresponding shared group with `source: LEGACY` and their original `periodIndex`, ID, date, order, text, and completion.
- Lunch/dinner indexes (100/101), unknown indexes, and invalid legacy day indexes are returned in `ungroupedItems`, without silently dropping data. Existing non-Monday week keys remain untouched and accessible through the old API; the new API requires an explicit Monday and does not normalize or merge those keys.
- New lunch/dinner plans also use `weekly_plan_items`: these really are period-specific items. They appear in `ungroupedItems` with `source: LEGACY`, `group: null`, and `periodIndex: 100` or `101`. Here `LEGACY` identifies the period-based storage, not the item's age. Existing targeted LEGACY update/delete routes handle them without a new source type or a whole-week replacement.
- Targeted edits to a legacy item change only its supplied text/completion. A targeted delete removes only the explicitly addressed item. Other days, weeks, goals, and members are untouched.
- New shared rows have `source: GROUP` and `periodIndex: null`. Always identify an item by **both source and ID**; IDs can overlap between the two tables.
- Response groups cover the whole week; filter by `dayIndex` (Monday=0 through Sunday=6) in the future editor. Within a day, legacy records retain period/order/ID ordering, followed by newly appended shared items. No reorder/move operation is introduced here.
- `shared_plan_weeks` stores a week revision and permanent legacy-save protection. Reads create no rows and activate nothing.
- Group deletions retain a tombstone and the original create token to prevent retry resurrection. Lunch/dinner creates record the original UUID, fingerprint, and item ID in `break_plan_create_receipts`. These receipts survive an item's explicit hard deletion; their item ID deliberately has no foreign key. Member deletion removes all new tables' records, including tombstones and receipts.

## Authenticated API

All routes use the existing JWT/current-member mechanism. Writes target only the authenticated member; request payloads never choose a member or branch. Manager reads use the existing weekly-plan policy: ADMIN can read across branches; STAFF can read ordinary members in their own branch; MEMBER cannot use manager reads.

Base path: `/api/weekly-plans/shared`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/me?weekStartDate=2026-10-26` | Read own week and current revision |
| GET | `/members/{memberId}?weekStartDate=2026-10-26` | Authorized manager read |
| POST | `/me/items` | Add one genuinely shared item |
| POST | `/me/break-items` | Add one lunch/dinner plan (period 100/101 only) |
| PATCH | `/me/goal` | Save or clear only the weekly goal |
| PATCH | `/me/items/{source}/{itemId}` | Edit text and/or completion in place |
| DELETE | `/me/items/{source}/{itemId}?weekStartDate=2026-10-26&expectedRevision=2` | Delete exactly one item |

`source` is `LEGACY` or `GROUP`. All successful operations return the complete `SharedPlanWeekResponse` (HTTP 200): `memberId`, `branchId`, `weekStartDate`, `revision`, `legacySaveProtected`, `goal`, `groups`, `ungroupedItems`. Each group contains `group` and `items`. Each item contains `id`, `source`, `dayIndex`, `periodIndex`, `group`, `content`, `done`, and `sortOrder`.

Create example (use the revision from the latest GET, not a hardcoded zero):

```json
{
  "weekStartDate": "2026-10-26",
  "expectedRevision": 0,
  "dayIndex": 0,
  "group": "PERIODS_1_2",
  "content": "민법 기출 20문제 풀기",
  "clientRequestId": "d53628ab-5f61-4704-839b-1fb554fb2145"
}
```

Patch example:

```json
{
  "weekStartDate": "2026-10-26",
  "expectedRevision": 1,
  "done": true
}
```

Patch `content` and `done` are independently optional; at least one must be non-null. Supplied content must be nonblank and is trimmed. Omitted content preserves the stored text exactly, including unusual legacy text. New tasks start incomplete. Week, day, group, original period, and order cannot be moved by PATCH.

Weekly-goal save example:

```json
{
  "weekStartDate": "2026-10-26",
  "expectedRevision": 2,
  "goal": "이번 주 민법 기출 100문제"
}
```

`goal` must be a string; `null` or omission returns 400. Whitespace is trimmed, and an empty string explicitly clears the goal. This updates only the selected week's goal, preserving an existing goal ID and all plan items. It shares the same week revision and protection as item mutations. Monthly goals are separate and unchanged.

Lunch/dinner create example:

```json
{
  "weekStartDate": "2026-10-26",
  "expectedRevision": 3,
  "dayIndex": 0,
  "periodIndex": 100,
  "content": "점심 후 오답 노트 복습",
  "clientRequestId": "e1038100-3416-4ba4-b0a9-9ee6a1086cd4"
}
```

`dayIndex` must be 0–6 and `periodIndex` must be 100 (lunch) or 101 (dinner). Content and UUID follow the group-create rules. The new item is appended after existing records for the same day and break period; existing rows and their order are never rewritten. To edit/complete/delete the returned item, use the existing `/me/items/LEGACY/{itemId}` routes. These plan entries do not start, stop, or change break-study sessions.

## Save safety and client integration

1. Fetch the week and retain its revision. Both public GET entrypoints use a repeatable-read snapshot so the revision, items, and goal are consistent.
2. Send one item or weekly-goal mutation with that revision. Every shared/legacy writer locks the same existing member row before inspecting state, including a week's first write.
3. Successful targeted mutations atomically increment the revision and permanently protect that week from old whole-week replacement. Lunch/dinner creation commits the item, receipt, revision, and protection together. Rejected operations do not activate a week or increment its revision.
4. A stale mutation returns **409**, not last-write-wins. Keep the user's unsaved input, refetch, and reconcile explicitly. Do not silently retry with a newly fetched revision or overwrite the whole week.
5. Serialize pending mutations per member/week in the editor. If requests nevertheless overlap, at most one mutation using a given revision succeeds; other changes need reconciliation.

Every create needs a fresh UUID for the user's **logical add action**. Keep that same UUID and original payload across network retries. Matching retries are recognized before the revision check and return the current snapshot without another item or revision increment. Reusing the UUID with different original week/day/group-or-break-period/text returns 409. Group and break creates share the same member-scoped UUID namespace: reusing a token across either creation type returns 409, including after deletion. A retry after the item was edited returns the current state; a retry after deletion does not resurrect it. A genuinely new add requires a new UUID.

PATCH/DELETE retries use normal revision checks: after a successful operation with a lost response, retrying its old revision returns 409. Refetch to determine whether the intended change already happened. Invalid input returns 400; an unavailable/deleted/foreign-member/wrong-week item returns 404 when the revision is current. Error bodies follow the existing `{ "message": "..." }` format.

Checklist completion has no effect on recognized study time. The future editor should continue displaying study metrics through their existing APIs/calculations.

## Existing unsaved drafts (Slice 2A)

The frontend application, session-storage keys, draft format, and existing save/recovery flow are deliberately unchanged. No draft is converted, uploaded, or assigned a server revision automatically. The new backend routes are not wired into the current editor yet, so this slice does not switch an open draft to a different save model.

Existing recovery continues to use member-scoped storage and local draft revisions. Valid weekly/monthly drafts retain their original fields, including lunch/dinner plans and unfinished entries. Successful-save cleanup checks the submitted period and draft revision; dirty drafts block server hydration and save-error callbacks do not clear them. No frontend application or test-file changes are included in this slice.

Important boundary for the next editor slice: a draft's `revision` is a **local draft identifier**, not the new backend's week revision. A recovered whole-week draft lacks a server baseline for safe automatic merging. Keep it available for explicit review/reconciliation; do not replace the server week or silently retry it with a freshly fetched revision. Existing malformed/unsupported draft handling and session-storage lifetime are unchanged by 2A; this is not a new durable or cross-device backup feature.

## Old planner compatibility and rollout

- Until a week has a successful **new item or weekly-goal API mutation**, the existing whole-week PUT and its response shape behave as before. Each successful old PUT now advances the new revision, invalidating an earlier new-editor snapshot.
- Once protected, **every** old `PUT /api/weekly-plans/me` for that week returns 409 **before updating its goal or deleting items**, including requests that appear to update only the weekly goal. Use the new targeted goal route for such weeks. Monthly-goal APIs remain unchanged.
- The old GET response remains unchanged and does not expose new shared items. Therefore an activated week must be read/edited through the new API; old plan views are not a complete combined view. The next frontend slice must update all relevant member/manager plan readers and stop whole-week saves for these weeks before enabling new writes.
- A backend-only release does not activate weeks by itself. Do not make manual production mutation calls as a smoke test: even a goal-only save activates the selected member/week.
- Schema preparation is additive: `backend/db/migrations/20261026_00_shared_plan_checklists.sql` creates the original two tables. Slice 2A leaves that migration unchanged and adds `20261026_01_break_plan_create_receipts.sql` for retry receipts only. There is no legacy data backfill. The repository currently uses Hibernate schema update rather than an automatic SQL migration runner; merely committing SQL files does not execute them. The operator must validate/apply the schema through the project's deployment process. No migration is executed by these development slices.
- Complete the compatible backend rollout on **all instances** before enabling the editor. An old backend instance can bypass the save guard or the new cross-kind token checks. Once targeted writes exist, do not roll back to an incompatible backend or drop the new tables. A safe rollback retains the storage, retry receipts, and protection and disables new writes until a compatible client is available.

## Verification boundaries

The automated suite uses a disposable local H2 database to exercise grouped reads, targeted mutations, revisions, concurrent writes, idempotency, existing-plan preservation, JWT/manager permissions, and member-deletion cleanup. Existing backend tests cover unrelated study/attendance behavior. These tests do not establish PostgreSQL migration execution, real-device UI, or production deployment success.

Run the backend tests without opting into external beverage PostgreSQL tests:

```sh
cd /Users/john/Desktop/studyfactory/backend
env -u BEVERAGE_TEST_JDBC_URL -u BEVERAGE_TEST_JDBC_USERNAME -u BEVERAGE_TEST_JDBC_PASSWORD ./gradlew test --no-daemon
```
