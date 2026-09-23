-- A branch and member name form the login identity. Duplicate identities make
-- every matching account impossible to log into, regardless of its PIN.
--
-- Do not guess how existing people should be renamed. If duplicates predate
-- this migration, stop the deploy and let an administrator assign deliberate
-- names such as John, John1, and John2 before retrying.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM members
         GROUP BY branch_id, name
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce unique member names per branch: duplicate (branch_id, name) rows exist.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uk_members_branch_name'
           AND conrelid = 'members'::regclass
    ) THEN
        ALTER TABLE members
            ADD CONSTRAINT uk_members_branch_name UNIQUE (branch_id, name);
    END IF;
END $$;

COMMIT;
