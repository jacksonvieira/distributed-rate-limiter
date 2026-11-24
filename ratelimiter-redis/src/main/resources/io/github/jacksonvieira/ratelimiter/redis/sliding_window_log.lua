-- Sliding window log, executed atomically by Redis.
--
-- KEYS[1]  sorted set holding one member per granted permit, scored by its grant time (microseconds)
-- ARGV[1]  limit       permits granted per sliding window
-- ARGV[2]  window_us   window length, in microseconds
-- ARGV[3]  permits     permits this request wants
-- ARGV[4]  nonce       unique per call, so members never collide even when Redis reports the same time twice
--
-- Returns {allowed (1|0), remaining, retry_after_us (0 when allowed)}.

local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local window_us = tonumber(ARGV[2])
local permits   = tonumber(ARGV[3])
local nonce     = ARGV[4]

-- Use Redis' clock, so every gateway instance agrees on the window.
local time = redis.call('TIME')
local now  = tonumber(time[1]) * 1000000 + tonumber(time[2])

-- A permit leaves the window once it is at least window_us old.
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window_us)

local used = redis.call('ZCARD', key)

if used + permits > limit then
    -- Enough room appears once the oldest (used + permits - limit) entries leave the window.
    local to_expire = used + permits - limit
    local blocking  = redis.call('ZRANGE', key, to_expire - 1, to_expire - 1, 'WITHSCORES')
    local wait      = tonumber(blocking[2]) + window_us - now
    return {0, limit - used, math.max(1, wait)}
end

for i = 1, permits do
    redis.call('ZADD', key, now, nonce .. '-' .. i)
end
redis.call('PEXPIRE', key, math.ceil(window_us / 1000) + 1)

return {1, limit - used - permits, 0}
