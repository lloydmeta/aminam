CREATE TABLE policy_attachments (
    id               UUID PRIMARY KEY,
    attached_to_type VARCHAR(16)  NOT NULL CHECK (attached_to_type IN ('MEMBERSHIP', 'DATABASE')),
    attached_to_id   UUID         NOT NULL,
    policy_id        VARCHAR(128) NOT NULL,
    CONSTRAINT policy_attachments_uq UNIQUE (attached_to_type, attached_to_id, policy_id)
);

CREATE INDEX policy_attachments_by_point ON policy_attachments(attached_to_type, attached_to_id);
