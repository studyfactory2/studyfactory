-- Study presence: make the manager's manual check-in note optional.
--
-- The operator and MANAGER method remain mandatory audit data. Blank notes are
-- normalized to NULL by the application. This migration replaces the stricter
-- named CHECK already installed by 20260903_00 without disturbing deployments
-- where the relaxed definition is already present.

BEGIN;

DO $$
DECLARE
    current_definition text;
BEGIN
    SELECT pg_get_constraintdef(oid)
      INTO current_definition
      FROM pg_constraint
     WHERE conname = 'ck_study_presence_sessions_check_in_audit'
       AND conrelid = 'study_presence_sessions'::regclass;

    IF current_definition IS NOT NULL
       AND lower(current_definition) LIKE '%manual_check_in_reason is not null%' THEN
        ALTER TABLE study_presence_sessions
            DROP CONSTRAINT ck_study_presence_sessions_check_in_audit;
        current_definition := NULL;
    END IF;

    IF current_definition IS NULL THEN
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

COMMIT;
