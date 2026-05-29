CREATE TABLE app_user (
    id           uuid        PRIMARY KEY,
    subject      text        NOT NULL UNIQUE,
    email        text        NOT NULL UNIQUE,
    display_name text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE invitation (
    id           uuid        PRIMARY KEY,
    token        text        NOT NULL UNIQUE,
    expires_at   timestamptz NOT NULL,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
