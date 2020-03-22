ALTER TABLE UM_USER
    ADD COLUMN UM_USER_ID CHAR(36) NOT NULL DEFAULT 'NONE',
    ADD UNIQUE(UM_USER_ID, UM_TENANT_ID);

UPDATE UM_USER SET UM_USER_ID = UUID();