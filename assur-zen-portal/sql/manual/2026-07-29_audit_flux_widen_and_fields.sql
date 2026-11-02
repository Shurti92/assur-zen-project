-- Fix: flux/exception_message were silently created as TINYTEXT (255 char max) because the
-- @Lob columns had no explicit length, truncating any real request/response payload.
-- Widen to MEDIUMTEXT. Hibernate's ddl-auto=update does not alter existing column types,
-- so this must be applied manually; the new status_code/ip_address/user_id/duration_ms
-- columns below are added automatically by Hibernate on next boot (ddl-auto=update) and are
-- listed here only for reference/idempotent re-application.

ALTER TABLE t_assur_zen_audit_flux
    MODIFY COLUMN flux MEDIUMTEXT NOT NULL,
    MODIFY COLUMN exception_message MEDIUMTEXT NULL;
