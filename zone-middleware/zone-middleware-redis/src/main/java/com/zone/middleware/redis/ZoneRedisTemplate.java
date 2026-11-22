package com.zone.middleware.redis;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 统一操作模板：String / Hash / List / Set / ZSet 五种结构的增删改查 + 批量能力。
 *
 * <p>值统一以 fastjson2 JSON 字符串存储（redis-cli 可直接阅读），
 * 读取时按调用方指定的目标类型还原。</p>
 */
public class ZoneRedisTemplate {

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造操作模板。
     *
     * @param redisTemplate 底层 StringRedisTemplate（key 与 value 均为字符串序列化）
     */
    public ZoneRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ==================== String ====================

    /**
     * 写入字符串值（无过期时间）。
     *
     * @param key   键，不能为空
     * @param value 值对象（fastjson2 序列化），不能为空
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, toJson(value));
    }

    /**
     * 写入字符串值并指定过期时间。
     *
     * @param key   键，不能为空
     * @param value 值对象（fastjson2 序列化），不能为空
     * @param ttl   过期时长
     */
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, toJson(value), ttl);
    }

    /**
     * 读取字符串值并按目标类型还原。
     *
     * @param key  键
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 还原后的对象；键不存在时返回 null
     */
    public <T> T get(String key, Class<T> type) {
        String raw = redisTemplate.opsForValue().get(key);
        return fromJson(raw, type);
    }

    // ==================== 计数 / 过期 / 简单互斥 ====================

    /**
     * 自增 1（整数字符串语义）。
     *
     * @param key 键
     * @return 自增后的值；键不存在时从 0 起自增
     */
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增指定步长（整数字符串语义）。
     *
     * @param key   键
     * @param delta 步长（可为负）
     * @return 自增后的值
     */
    public Long incrBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 读取键剩余存活时间（秒）。
     *
     * @param key 键
     * @return 剩余秒数；-1 表示永不过期，-2 表示键不存在
     */
    public Long ttl(String key) {
        return redisTemplate.getExpire(key);
    }

    /**
     * 设置键的存活时间（秒）。
     *
     * @param key     键
     * @param seconds 存活秒数
     * @return 是否设置成功（键不存在返回 false）
     */
    public Boolean expire(String key, long seconds) {
        return redisTemplate.expire(key, Duration.ofSeconds(seconds));
    }

    /**
     * 键不存在时写入并返回 true（简易互斥/防重入原语，适配固定 TTL 场景；已存在时不覆盖原值）。
     *
     * @param key   键
     * @param value 值对象（fastjson2 序列化）
     * @param ttl   过期时长
     * @return true = 抢占成功；false = 键已存在
     */
    public Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, toJson(value), ttl);
    }

    /**
     * 删除单个键。
     *
     * @param key 键
     * @return 是否删除成功（键不存在返回 false）
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    // ==================== 批量 ====================

    /**
     * 多键批量读取。
     *
     * @param keys 键集合（与返回列表一一对应）
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 与键顺序对应的值列表；不存在的键对应 null
     */
    public <T> List<T> mGet(Collection<String> keys, Class<T> type) {
        List<String> raws = redisTemplate.opsForValue().multiGet(keys);
        if (raws == null) {
            return keys.stream().map(k -> (T) null).collect(Collectors.toList());
        }
        return raws.stream().map(raw -> fromJson(raw, type)).collect(Collectors.toList());
    }

    /**
     * 多键批量删除。
     *
     * @param keys 键集合
     * @return 实际删除的键数量
     */
    public Long deleteKeys(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // ==================== Hash ====================

    /**
     * 写入哈希字段。
     *
     * @param key   键，不能为空
     * @param field 字段名，不能为空
     * @param value 字段值对象（fastjson2 序列化），不能为空
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.<String, String>opsForHash().put(key, field, toJson(value));
    }

    /**
     * 读取哈希字段并按目标类型还原。
     *
     * @param key   键
     * @param field 字段名
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 还原后的字段值；字段不存在时返回 null
     */
    public <T> T hGet(String key, String field, Class<T> type) {
        String raw = redisTemplate.<String, String>opsForHash().get(key, field);
        return fromJson(raw, type);
    }

    /**
     * 读取整个哈希并按目标类型还原。
     *
     * @param key  键
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 字段名到值的有序映射；键不存在时返回空 Map
     */
    public <T> Map<String, T> hGetAll(String key, Class<T> type) {
        Map<String, String> raw = redisTemplate.<String, String>opsForHash().entries(key);
        Map<String, T> result = new LinkedHashMap<>(raw.size());
        raw.forEach((field, value) -> result.put(field, fromJson(value, type)));
        return result;
    }

    /**
     * 批量删除哈希字段。
     *
     * @param key    键
     * @param fields 待删除的字段名集合
     * @return 实际删除的字段数量
     */
    public Long hDel(String key, String... fields) {
        return redisTemplate.<String, String>opsForHash().delete(key, (Object[]) fields);
    }

    // ==================== List ====================

    /**
     * 列表左侧入队。
     *
     * @param key   键，不能为空
     * @param value 值对象（fastjson2 序列化），不能为空
     * @return 入队后的列表长度
     */
    public Long lPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, toJson(value));
    }

    /**
     * 列表右侧入队。
     *
     * @param key   键，不能为空
     * @param value 值对象（fastjson2 序列化），不能为空
     * @return 入队后的列表长度
     */
    public Long rPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, toJson(value));
    }

    /**
     * 按下标修改列表元素。
     *
     * @param key   键
     * @param index 下标（0 起，负数从尾部起算）
     * @param value 新值对象，不能为空
     */
    public void lSet(String key, long index, Object value) {
        redisTemplate.opsForList().set(key, index, toJson(value));
    }

    /**
     * 区间读取列表元素。
     *
     * @param key   键
     * @param start 起始下标（0 起）
     * @param end   结束下标（-1 表示末尾）
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 按入队顺序排列的元素列表；键不存在时返回空列表
     */
    public <T> List<T> lRange(String key, long start, long end, Class<T> type) {
        List<String> raws = redisTemplate.opsForList().range(key, start, end);
        if (raws == null) {
            return List.of();
        }
        return raws.stream().map(raw -> fromJson(raw, type)).collect(Collectors.toList());
    }

    /**
     * 按值移除列表元素。
     *
     * @param key   键
     * @param count 移除数量（正数从头部起算，负数从尾部起算，0 全部）
     * @param value 匹配值对象
     * @return 实际移除数量
     */
    public Long lRem(String key, long count, Object value) {
        return redisTemplate.opsForList().remove(key, count, toJson(value));
    }

    /**
     * 获取列表长度。
     *
     * @param key 键
     * @return 列表长度；键不存在时返回 null
     */
    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // ==================== Set ====================

    /**
     * 添加集合成员（可多个）。
     *
     * @param key    键，不能为空
     * @param values 成员对象（fastjson2 序列化），不能包含 null
     * @return 新增成员数量
     */
    public Long sAdd(String key, Object... values) {
        String[] jsons = toJsonArray(values);
        return redisTemplate.opsForSet().add(key, jsons);
    }

    /**
     * 读取集合全部成员。
     *
     * @param key  键
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 成员集合；键不存在时返回空集合
     */
    public <T> Set<T> sMembers(String key, Class<T> type) {
        Set<String> raws = redisTemplate.opsForSet().members(key);
        if (raws == null) {
            return Set.of();
        }
        return raws.stream().map(raw -> fromJson(raw, type)).collect(Collectors.toSet());
    }

    /**
     * 判断成员是否存在于集合。
     *
     * @param key   键
     * @param value 匹配值对象
     * @return 存在返回 true；不存在或键缺失返回 false
     */
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, toJson(value));
    }

    /**
     * 移除集合成员（可多个）。
     *
     * @param key    键
     * @param values 待移除成员对象
     * @return 实际移除数量
     */
    public Long sRem(String key, Object... values) {
        String[] jsons = toJsonArray(values);
        return redisTemplate.opsForSet().remove(key, (Object[]) jsons);
    }

    /**
     * 获取集合基数（成员数）。
     *
     * @param key 键
     * @return 成员数量；键不存在时返回 null
     */
    public Long sCard(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // ==================== ZSet ====================

    /**
     * 写入有序集合成员与分值。
     *
     * @param key   键，不能为空
     * @param value 成员对象（fastjson2 序列化），不能为空
     * @param score 分值
     * @return true 表示新增；false 表示分值更新
     */
    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, toJson(value), score);
    }

    /**
     * 按排名区间升序读取有序集合成员。
     *
     * @param key   键
     * @param start 起始排名（0 起）
     * @param end   结束排名（-1 表示末尾）
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 按分值升序排列的成员列表；键不存在时返回空列表
     */
    public <T> List<T> zRange(String key, long start, long end, Class<T> type) {
        Set<String> raws = redisTemplate.opsForZSet().range(key, start, end);
        if (raws == null) {
            return List.of();
        }
        return raws.stream().map(raw -> fromJson(raw, type)).collect(Collectors.toList());
    }

    /**
     * 按排名区间降序读取有序集合成员。
     *
     * @param key   键
     * @param start 起始排名（0 起）
     * @param end   结束排名（-1 表示末尾）
     * @param type  目标类型
     * @param <T>   目标类型
     * @return 按分值降序排列的成员列表；键不存在时返回空列表
     */
    public <T> List<T> zRevRange(String key, long start, long end, Class<T> type) {
        Set<String> raws = redisTemplate.opsForZSet().reverseRange(key, start, end);
        if (raws == null) {
            return List.of();
        }
        return raws.stream().map(raw -> fromJson(raw, type)).collect(Collectors.toList());
    }

    /**
     * 读取有序集合成员的分值。
     *
     * @param key   键
     * @param value 成员对象
     * @return 成员分值；成员不存在时返回 null
     */
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, toJson(value));
    }

    /**
     * 移除有序集合成员（可多个）。
     *
     * @param key    键
     * @param values 待移除成员对象
     * @return 实际移除数量
     */
    public Long zRem(String key, Object... values) {
        String[] jsons = toJsonArray(values);
        return redisTemplate.opsForZSet().remove(key, (Object[]) jsons);
    }

    /**
     * 获取有序集合基数（成员数）。
     *
     * @param key 键
     * @return 成员数量；键不存在时返回 null
     */
    public Long zCard(String key) {
        return redisTemplate.opsForZSet().zCard(key);
    }

    // ==================== 序列化 ====================

    /**
     * 值对象序列化为 JSON 文本。
     *
     * @param value 值对象，不能为空
     * @return JSON 字符串
     * @throws NullPointerException value 为 null
     */
    private String toJson(Object value) {
        Objects.requireNonNull(value, "value 不能为空");
        return com.alibaba.fastjson2.JSON.toJSONString(value);
    }

    /**
     * JSON 文本反序列化为目标类型。
     *
     * @param raw  JSON 字符串，可为 null
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 还原对象；raw 为 null 时返回 null
     */
    private <T> T fromJson(String raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        return com.alibaba.fastjson2.JSON.parseObject(raw, type);
    }

    /**
     * 变参数组整体序列化为 JSON 数组。
     *
     * @param values 值对象数组
     * @return 与入参等长的 JSON 字符串数组
     */
    private String[] toJsonArray(Object[] values) {
        String[] jsons = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            jsons[i] = toJson(values[i]);
        }
        return jsons;
    }
}
