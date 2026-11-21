package com.zone.middleware.http;

import com.zone.middleware.log.ZoneLog;

import java.time.Duration;

/**
 * 简单熔断器（单实例内存态，连续失败计数模型）：
 * <ul>
 *   <li>CLOSED：正常放行，连续失败达到阈值进入 OPEN</li>
 *   <li>OPEN：冷却期内直接拒绝；冷却结束进入 HALF_OPEN</li>
 *   <li>HALF_OPEN：放行探测请求，成功恢复 CLOSED、失败重回 OPEN</li>
 * </ul>
 */
public final class ZoneCircuitBreaker {

    /** 熔断器状态 */
    public enum State {
        /** 关闭：正常放行 */
        CLOSED,
        /** 开启：冷却期内直接拒绝 */
        OPEN,
        /** 半开：冷却结束，放行探测请求 */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openWaitDuration;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAtMillis = 0L;

    /**
     * 构建熔断器。
     *
     * @param failureThreshold 连续失败阈值（达到即 OPEN），必须为正
     * @param openWaitDuration OPEN 冷却时长，null 回退 30s
     * @throws IllegalArgumentException failureThreshold 非正数
     */
    public ZoneCircuitBreaker(int failureThreshold, Duration openWaitDuration) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold 必须为正数: " + failureThreshold);
        }
        this.failureThreshold = failureThreshold;
        this.openWaitDuration = openWaitDuration == null ? Duration.ofSeconds(30) : openWaitDuration;
    }

    /**
     * 判定是否放行请求：OPEN 冷却期内返回 false；冷却结束迁移 HALF_OPEN 并放行探测。
     *
     * @return 放行返回 true；熔断拒绝返回 false
     */
    public synchronized boolean tryAcquire() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis >= openWaitDuration.toMillis()) {
                state = State.HALF_OPEN;
                ZoneLog.info("ZoneCircuitBreaker", "tryAcquire",
                        "冷却结束（{}ms），熔断进入 HALF_OPEN 放行探测", openWaitDuration.toMillis());
                return true;
            }
            return false;
        }
        return true;
    }

    /** 记录一次成功：HALF_OPEN 探测成功恢复 CLOSED。 */
    public synchronized void recordSuccess() {
        if (state == State.HALF_OPEN) {
            ZoneLog.info("ZoneCircuitBreaker", "recordSuccess", "探测成功，熔断恢复 CLOSED");
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    /** 记录一次失败：HALF_OPEN 探测失败重回 OPEN；CLOSED 下连续失败达阈值进入 OPEN。 */
    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAtMillis = System.currentTimeMillis();
            consecutiveFailures = 0;
            ZoneLog.warn("ZoneCircuitBreaker", "recordFailure",
                    "连续失败达阈值（{}），熔断进入 OPEN", failureThreshold);
        }
    }

    /**
     * 获取当前状态（惰性迁移：OPEN 冷却已到时对外呈现 HALF_OPEN）。
     *
     * @return 当前熔断状态
     */
    public synchronized State getState() {
        if (state == State.OPEN
                && System.currentTimeMillis() - openedAtMillis >= openWaitDuration.toMillis()) {
            // 惰性迁移：查询时冷却已到，对外呈现 HALF_OPEN（下次 tryAcquire 放行探测）
            return State.HALF_OPEN;
        }
        return state;
    }
}
