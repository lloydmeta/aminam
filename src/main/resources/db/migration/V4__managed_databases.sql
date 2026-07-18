CREATE TABLE managed_databases (
    id         UUID PRIMARY KEY,
    org_id     UUID NOT NULL REFERENCES orgs(id),
    name       VARCHAR(255) NOT NULL,   -- the single editable field; not unique within an org
    created_by UUID NOT NULL REFERENCES principals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX managed_databases_by_org_created_at ON managed_databases(org_id, created_at);
