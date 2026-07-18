CREATE TABLE orgs (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL REFERENCES principals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
    id           UUID PRIMARY KEY,
    principal_id UUID NOT NULL REFERENCES principals(id),
    org_id       UUID NOT NULL REFERENCES orgs(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT memberships_uq UNIQUE (principal_id, org_id)
);

CREATE INDEX memberships_by_org_created_at ON memberships(org_id, created_at);
CREATE INDEX memberships_by_principal      ON memberships(principal_id);
