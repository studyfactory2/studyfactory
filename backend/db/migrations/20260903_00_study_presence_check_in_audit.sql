-- Study presence: check-in audit columns (method / author / optional manual note).
--
-- deploy.yml applies every file in db/migrations/*.sql, in glob order, on each
-- deploy, BEFORE the new backend container is built and swapped in. Two
-- consequences shape this file:
--
--   1. While it runs, and until the swap completes, the PREVIOUS backend is
--      still serving traffic and still inserting presence rows that do not
--      mention the audit columns at all. The same is true permanently after
--      restore_previous_backend() rolls the image back. So the columns stay
--      nullable and the CHECK explicitly admits that legacy shape. Manager
--      rows still require an author; their explanatory note is optional.
--   2. It is re-applied on every deploy, so every statement is idempotent and
--      the backfill re-heals any legacy rows written in the meantime.

BEGIN;

ALTER TABLE study_presence_sessions
    ADD COLUMN IF NOT EXISTS check_in_method varchar(20);

ALTER TABLE study_presence_sessions
    ADD COLUMN IF NOT EXISTS checked_in_by_member_id bigint;

ALTER TABLE study_presence_sessions
    ADD COLUMN IF NOT EXISTS manual_check_in_reason varchar(200);

-- Every row written before this change, and every row a rolled-back backend
-- writes afterwards, is a member's own QR scan: they are their own check-in
-- author and there is no correction reason to record.
UPDATE study_presence_sessions
   SET check_in_method = 'QR',
       checked_in_by_member_id = member_id
 WHERE check_in_method IS NULL
   AND checked_in_by_member_id IS NULL
   AND manual_check_in_reason IS NULL;

-- No foreign key on checked_in_by_member_id on purpose: an operator or member
-- may be deleted later, and an audit trail that vanishes with them (or blocks
-- the delete) is worse than a dangling identifier.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_study_presence_sessions_check_in_audit'
          AND conrelid = 'study_presence_sessions'::regclass
    ) THEN
        ALTER TABLE study_presence_sessions
            ADD CONSTRAINT ck_study_presence_sessions_check_in_audit CHECK (
                (check_in_method IS NULL
                    AND checked_in_by_member_id IS NULL
                    AND manual_check_in_reason IS NULL)
                OR (check_in_method = 'QR'
                    AND checked_in_by_member_id = member_id
                    AND manual_check_in_reason IS NULL)
                OR (check_in_method = 'MANAGER'
                    AND checked_in_by_member_id IS NOT NULL
                    AND (manual_check_in_reason IS NULL
                        OR manual_check_in_reason <> ''))
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_study_presence_sessions_check_in_method
    ON study_presence_sessions (check_in_method, checked_in_at);

COMMIT;
