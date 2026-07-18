CREATE TABLE policies (
    id         UUID PRIMARY KEY,
    org_id     UUID NOT NULL REFERENCES orgs(id),   -- owner / namespace
    name       VARCHAR(255) NOT NULL,
    document   JSONB NOT NULL,                       -- the policy document (statements)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX policies_by_org_created_at ON policies(org_id, created_at);
