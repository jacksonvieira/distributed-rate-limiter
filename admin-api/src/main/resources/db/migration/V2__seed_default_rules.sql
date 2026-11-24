insert into rate_limit_rule (position, name, path_prefix, capacity, refill_tokens, refill_period_ms, algorithm, key_strategy)
values (1, 'default-api', '/api/', 100, 100, 60000, 'TOKEN_BUCKET', 'IP'),
       (2, 'default-fallback', '/', 1000, 1000, 60000, 'FIXED_WINDOW', 'IP');
