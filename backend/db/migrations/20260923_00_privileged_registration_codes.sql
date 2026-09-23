-- One-time registration codes for pending STAFF and ADMIN accounts.
--
-- Plaintext codes are never stored. Existing pending privileged accounts stay
-- pending until an administrator issues a new code through the application.

BEGIN;

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS registration_code_hash varchar(64),
    ADD COLUMN IF NOT EXISTS registration_code_expires_at timestamp,
    ADD COLUMN IF NOT EXISTS registration_code_failed_attempts integer NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'ck_members_registration_code_attempts'
           AND conrelid = 'members'::regclass
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT ck_members_registration_code_attempts
            CHECK (registration_code_failed_attempts BETWEEN 0 AND 5);
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'ck_members_registration_code_state'
           AND conrelid = 'members'::regclass
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT ck_members_registration_code_state
            CHECK (
                (registration_code_hash IS NULL
                    AND registration_code_expires_at IS NULL
                    AND registration_code_failed_attempts = 0)
                OR (registration_code_hash IS NOT NULL
                    AND registration_code_expires_at IS NOT NULL)
            );
    END IF;
END $$;

COMMIT;
