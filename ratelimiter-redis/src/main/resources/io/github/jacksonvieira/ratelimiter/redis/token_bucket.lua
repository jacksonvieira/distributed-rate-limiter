-- Token bucket, executed atomically by Redis.
--
-- KEYS[1]  hash holding the bucket: field "tokens" (may be fractional) and "ts" (last refill, in µs)
-- ARGV[1]  capacity             maximum number of tokens
-- ARGV[2]  refill_tokens        tokens added per refill period
-- ARGV[3]  refill_period_us     refill period, in microseconds
-- ARGV[4]  permits              tokens this request wants to consume
--
-- Returns {allowed (1|0), remaining (whole tokens left), retry_after_us (0 when allowed)}.
-- Integers only: Redis truncates Lua numbers to integers in replies anyway.

local key              = KEYS[1]
local capacity         = tonumber(ARGV[1])
local refill_tokens    = tonumber(ARGV[2])
local refill_period_us = tonumber(ARGV[3])
local permits          = tonumber(ARGV[4])

-- Use Redis' clock, so every gateway instance sees the same "now" regardless of its own clock.
local time = redis.call('TIME')
local now  = tonumber(time[1]) * 1000000 + tonumber(time[2])

-- A missing bucket is a full bucket (it was never used, or it expired after filling up again).
local state  = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local last   = tonumber(state[2]) or now

local allowed        = 0
local retry_after_us = 0

local elapsed_us = math.max(now - last, 0)
tokens = math.min(capacity, tokens + elapsed_us * refill_tokens / refill_period_us)

if tokens >= permits then
    tokens = tokens - permits
    allowed = 1
else
    retry_after_us = (permits - tokens) * refill_period_us / refill_tokens
end

redis.call('HSET', key, 'tokens', tokens, 'ts', now)
-- Expire once the bucket would be full again: from then on a missing key means the same thing.
local full_after_ms = math.ceil((capacity - tokens) * refill_period_us / refill_tokens / 1000)
redis.call('PEXPIRE', key, math.max(full_after_ms, 1))

return {allowed, math.floor(tokens), math.ceil(retry_after_us)}
