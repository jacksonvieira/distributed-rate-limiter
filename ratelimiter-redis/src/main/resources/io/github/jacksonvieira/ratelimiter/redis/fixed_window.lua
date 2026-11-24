-- Fixed window counter, executed atomically by Redis.
--
-- KEYS[1]  hash holding the counter: field "window" (index of the current window) and "used"
-- ARGV[1]  limit       permits granted per window
-- ARGV[2]  window_us   window length, in microseconds
-- ARGV[3]  permits     permits this request wants
--
-- Returns {allowed (1|0), remaining, retry_after_us (0 when allowed)}.

local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local window_us = tonumber(ARGV[2])
local permits   = tonumber(ARGV[3])

-- Use Redis' clock, so every gateway instance agrees on where the windows are.
local time   = redis.call('TIME')
local now    = tonumber(time[1]) * 1000000 + tonumber(time[2])
local window = math.floor(now / window_us)
local ends_in_us = (window + 1) * window_us - now

local state = redis.call('HMGET', key, 'window', 'used')
local used  = 0
if tonumber(state[1]) == window then
    used = tonumber(state[2])
end

if used + permits > limit then
    return {0, limit - used, ends_in_us}
end

used = used + permits
redis.call('HSET', key, 'window', window, 'used', used)
-- The counter is meaningless once its window is over, so let Redis drop it then.
redis.call('PEXPIRE', key, math.max(math.ceil(ends_in_us / 1000), 1))

return {1, limit - used, 0}
