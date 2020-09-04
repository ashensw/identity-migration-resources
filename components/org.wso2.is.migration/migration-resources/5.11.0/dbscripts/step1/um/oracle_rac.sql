CREATE TABLE UM_HYBRID_GROUP_ROLE(
            UM_ID INTEGER NOT NULL,
            UM_GROUP_NAME VARCHAR(255),
            UM_ROLE_ID INTEGER NOT NULL,
            UM_TENANT_ID INTEGER DEFAULT 0,
            UM_DOMAIN_ID INTEGER,
            UNIQUE (UM_GROUP_NAME, UM_ROLE_ID, UM_TENANT_ID, UM_DOMAIN_ID),
            FOREIGN KEY (UM_ROLE_ID, UM_TENANT_ID) REFERENCES UM_HYBRID_ROLE(UM_ID, UM_TENANT_ID) ON DELETE CASCADE,
            FOREIGN KEY (UM_DOMAIN_ID, UM_TENANT_ID) REFERENCES UM_DOMAIN(UM_DOMAIN_ID,UM_TENANT_ID) ON DELETE CASCADE,
            PRIMARY KEY (UM_ID, UM_TENANT_ID)
)
/
CREATE SEQUENCE UM_HYBRID_GROUP_ROLE_SEQUENCE START WITH 1 INCREMENT BY 1 CACHE 20 ORDER
/
CREATE OR REPLACE TRIGGER UM_HYBRID_GROUP_ROLE_TRIGGER
                    BEFORE INSERT
                    ON UM_HYBRID_GROUP_ROLE
                    REFERENCING NEW AS NEW
                    FOR EACH ROW
                    BEGIN
                    SELECT UM_HYBRID_GROUP_ROLE_SEQUENCE.nextval INTO :NEW.UM_ID FROM dual;
              END;
/
