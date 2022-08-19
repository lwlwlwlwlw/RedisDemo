-- 得到锁的key
local key = KEYS[1]
-- 根据 key 获取 value
local id = redis.call('get', key)
-- 得到当前线程的id
local threadKey = ARGV[1]
-- 比较两个标识是否一致
if (id == threadKey) then
    -- 如果一致则释放锁
    return redis.call('del', key)
end
return 0