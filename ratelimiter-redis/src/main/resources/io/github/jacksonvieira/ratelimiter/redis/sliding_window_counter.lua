-- Sliding window counter, executed atomically by Redis.
--
-- KEYS[1]  hash holding the counters: "window" (index of the current window), "current" and "previous"
-- ARGV[1]  limit       permits granted per sliding window
-- ARGV[2]  window_us   window length, in microseconds
-- ARGV[3]  permits     permits this request wants
--
-- The load in the sliding window is estimated as previous * (1 - elapsed fraction) + current.
-- Returns {allowed (1|0), remaining, retry_after_us (0 when allowed)}.

local key       = KEYS[1]
local limit     = tonumber(ARGV[1])
local window_us = tonumber(ARGV[2])
local permits   = tonumber(ARGV[3])

-- Use Redis' clock, so every gateway instance agrees on where the windows are.
local time    = redis.call('TIME')
local now     = tonumber(time[1]) * 1000000 + tonumber(time[2])
local window  = math.floor(now / window_us)
local elapsed = (now - window * window_us) / window_us

local state    = redis.call('HMGET', key, 'window', 'current', 'previous')
local stored   = tonumber(state[1])
local current  = tonumber(state[2]) or 0
local previous = tonumber(state[3]) or 0

if stored == nil or window > stored + 1 then
    previous = 0
    current  = 0
elseif window == stored + 1 then
    previous = current
    current  = 0
end

local estimated = previous * (1 - elapsed) + current

if estimated + permits > limit then
    -- The estimate only falls as the previous window fades, so wait until it fits,
    -- possibly after the windows roll over.
    local slack = limit - permits - current
    local wait
    if slack >= 0 and previous > 0 then
        wait = (1 - slack / previous) - elapsed
    else
        local fade = 0
        if current > 0 then
            fade = math.max(0, 1 - (limit - permits) / current)
        end
        wait = (1 - elapsed) + fade
    end
    local remaining = math.max(0, math.floor(limit - estimated))
    return {0, remaining, math.max(1, math.ceil(wait * window_us))}
end

current = current + permits
redis.call('HSET', key, 'window', window, 'current', current, 'previous', previous)
-- Both counters are irrelevant once the next window is over as well.
local ttl_ms = math.ceil(((window + 2) * window_us - now) / 1000)
redis.call('PEXPIRE', key, math.max(ttl_ms, 1))

return {1, math.max(0, math.floor(limit - (estimated + permits))), 0}
