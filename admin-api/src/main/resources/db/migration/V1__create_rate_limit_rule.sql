create table rate_limit_rule (
    id               bigserial primary key,
    position         integer      not null,
    name             varchar(100) not null unique,
    path_prefix      varchar(255),
    plan             varchar(100),
    capacity         bigint check (capacity > 0),
    refill_tokens    bigint check (refill_tokens > 0),
    refill_period_ms bigint check (refill_period_ms > 0),
    algorithm        varchar(30),
    key_strategy     varchar(10),
    key_header       varchar(100),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now()
);

create index rate_limit_rule_position_idx on rate_limit_rule (position, id);
