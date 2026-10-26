# Shared plan checklists: backend foundation

This slice adds storage and item-level save safety for the three daily checklists. It does not change the frontend, study-time calculations, QR/presence, break-study controls, attendance, or permissions. The inline editor is a separate slice.

## Storage and preservation

| Group value | Display label | Existing zero-based period indexes |
| --- | --- | --- |
| `PERIODS_1_2` | 1–2교시 | 0, 1 |
| `PERIODS_3_5` | 3–5교시 | 2, 3, 4 |
| `PERIODS_6_7` | 6–7교시 | 5, 6 |

- New checklist items live once in `shared_plan_items`, identified by member, week, day, and group. They are not assigned to a pretend single period or copied into every period.
- Existing `weekly_plan_items` are not migrated, merged, deduplicated, or rewritten on read. Regular items appear in the corresponding shared group with `source: LEGACY` and their original `periodIndex`, ID, date, order, text, and completion.
- Lunch/dinner indexes (100/101), unknown indexes, and invalid legacy day indexes are returned in `ungroupedItems`, without silently dropping data. Existing non-Monday week keys remain untouched and accessible through the old API; the new API requires an explicit Monday and does not normalize or merge those keys.
- Targeted edits to a legacy item change only its supplied text/completion. A targeted delete removes only the explicitly addressed item. Other days, weeks, goals, and members are untouched.
- New shared rows have `source: GROUP` and `periodIndex: null`. Always identify an item by **both source and ID**; IDs can overlap between the two tables.
- Response groups cover the whole week; filter by `dayIndex` (Monday=0 through Sunday=6) in the future editor. Within a day, legacy records retain period/order/ID ordering, followed by newly appended shared items. No reorder/move operation is introduced here.
- `shared_plan_weeks` stores a week revision and permanent legacy-save protection. Reads create no rows and activate nothing.
- Group deletions retain a tombstone and the original create token to prevent retry resurrection. Member deletion removes both new tables' records, including tombstones.

## Authenticated API

All routes use the existing JWT/current-member mechanism. Writes target only the authenticated member; request payloads never choose a member or branch. Manager reads use the existing weekly-plan policy: ADMIN can read across branches; STAFF can read ordinary members in their own branch; MEMBER cannot use manager reads.

Base path: `/api/weekly-plans/shared`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/me?weekStartDate=2026-10-26` | Read own week and current revision |
| GET | `/members/{memberId}?weekStartDate=2026-10-26` | Authorized manager read |
| POST | `/me/items` | Add one genuinely shared item |
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

## Save safety and client integration

1. Fetch the week and retain its revision. Both public GET entrypoints use a repeatable-read snapshot so the revision, items, and goal are consistent.
2. Send one item mutation with that revision. Every shared/legacy writer locks the same existing member row before inspecting state, including a week's first write.
3. Successful item mutations atomically increment the revision and permanently protect that week from old whole-week replacement. Rejected operations do not activate a week or increment its revision.
4. A stale mutation returns **409**, not last-write-wins. Keep the user's unsaved input, refetch, and reconcile explicitly. Do not silently retry with a newly fetched revision or overwrite the whole week.
5. Serialize pending mutations per member/week in the editor. If requests nevertheless overlap, at most one mutation using a given revision succeeds; other changes need reconciliation.

Every create needs a fresh UUID for the user's **logical add action**. Keep that same UUID and original payload across network retries. Matching retries are recognized before the revision check and return the current snapshot without another item or revision increment. Reusing the UUID with different original week/day/group/text returns 409. A retry after the item was edited returns the current state; a retry after deletion does not resurrect it. A genuinely new add requires a new UUID. This idempotency rule is scoped to the authenticated member.

PATCH/DELETE retries use normal revision checks: after a successful operation with a lost response, retrying its old revision returns 409. Refetch to determine whether the intended change already happened. Invalid input returns 400; an unavailable/deleted/foreign-member/wrong-week item returns 404 when the revision is current. Error bodies follow the existing `{ "message": "..." }` format.

Checklist completion has no effect on recognized study time. The future editor should continue displaying study metrics through their existing APIs/calculations.

## Old planner compatibility and rollout

- Until a week has a successful **new item API mutation**, the existing whole-week PUT and its response shape behave as before. Each successful old PUT now advances the new revision, invalidating an earlier new-editor snapshot.
- Once protected, **every** old `PUT /api/weekly-plans/me` for that week returns 409 **before updating its goal or deleting items**, including requests that appear to update only the weekly goal. Monthly-goal APIs remain unchanged. No new weekly-goal writer is included in this slice.
- The old GET response remains unchanged and does not expose new shared items. Therefore an activated week must be read/edited through the new API; old plan views are not a complete combined view. The next frontend slice must update all relevant member/manager plan readers and stop whole-week saves for these weeks before enabling new writes.
- A backend-only release does not activate weeks by itself. Do not make manual production create calls as a smoke test: they activate the selected member/week.
- Schema preparation is additive: `backend/db/migrations/20261026_00_shared_plan_checklists.sql` creates only the two new tables, indexes, and checks. There is no legacy data backfill. The repository currently uses Hibernate schema update rather than an automatic SQL migration runner; merely committing this SQL file does not execute it. The operator must validate/apply the schema through the project's deployment process. No migration is executed by this development slice.
- Complete the backend rollout on **all instances** before enabling the editor. An old backend instance does not know about the save guard and could bypass protection. Once grouped writes exist, do not roll back to a backend without the guard or drop the new tables. A safe rollback retains the storage and protection and disables new writes until a compatible client is available.

## Verification boundaries

The automated suite uses a disposable local H2 database to exercise grouped reads, targeted mutations, revisions, concurrent writes, idempotency, existing-plan preservation, JWT/manager permissions, and member-deletion cleanup. Existing backend tests cover unrelated study/attendance behavior. These tests do not establish PostgreSQL migration execution, real-device UI, or production deployment success.

Run the backend tests without opting into external beverage PostgreSQL tests:

```sh
cd /Users/john/Desktop/studyfactory/backend
env -u BEVERAGE_TEST_JDBC_URL -u BEVERAGE_TEST_JDBC_USERNAME -u BEVERAGE_TEST_JDBC_PASSWORD ./gradlew test --no-daemon
```
