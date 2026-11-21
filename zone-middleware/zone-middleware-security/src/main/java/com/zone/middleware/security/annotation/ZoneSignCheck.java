package com.zone.middleware.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口签名校验声明：标注的端点在收到请求时执行签名校验链。
 *
 * <p>校验内容（fail-closed，任一环节失败即拒绝）：请求头 {@code Zone-Access-Token}
 * 签名比对、platform 分流策略（web 免 deviceId；pc/mobile/pad 必填且格式校验）、
 * timestamp 有效时间窗、reqId 防重放；{@link #requireLogin()} 为 true 时在签名比对
 * 通过后、防重放消费前追加登录态校验（请求头 {@code Zone-Auth-Token}），通过后业务
 * 代码可经 {@code ZoneSecurityContext} 取用登录用户信息。</p>
 *
 * <p>属性说明：</p>
 * <ul>
 *   <li>{@link #timestampWindowMillis()}：本端点请求时间窗（毫秒，与 {@code timestamp}
 *       字段同为 Epoch 毫秒精度，如 5 分钟为 {@code 300000}）；缺省 {@code 0} 继承全局配置
 *       {@code zone.middleware.security.sign.timestamp-window}；nonce 生存期跟随本端点
 *       有效时间窗；负值属配置错误，该端点 fail-closed 拒绝。</li>
 *   <li>{@link #requireLogin()}：是否强制登录；缺省 false 时端点行为与无属性标注
 *       完全一致（纯签名校验）。</li>
 * </ul>
 *
 * <p>标注位置：Controller 类或接口方法均可；方法级声明优先于类级，并支持以本注解为
 * 元注解的组合注解。未标注的端点完全不受拦截。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * // 敏感端点：窗口收紧到 60 秒并强制登录
 * @ZoneSignCheck(timestampWindowMillis = 60_000, requireLogin = true)
 * @PostMapping("/pay")
 * public ZoneResult<String> pay(@RequestBody PayRequest request) {
 *     Long userId = ZoneSecurityContext.currentUserId();
 *     ...
 * }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZoneSignCheck {

    /**
     * 本端点请求时间窗（毫秒）：timestamp 与服务器时间允许的最大偏差。
     *
     * <p>缺省 {@code 0} 表示继承全局配置 {@code zone.middleware.security.sign.timestamp-window}；
     * 正值为本端点覆盖值（如 60 秒为 {@code 60_000}）；负值属配置错误——该端点
     * fail-closed 全部拒绝并记录服务端错误日志。防重放 nonce 生存期与本有效时间窗一致。</p>
     *
     * @return 时间窗毫秒数；0 表示继承全局
     */
    long timestampWindowMillis() default 0;

    /**
     * 是否强制登录。
     *
     * <p>true 时端点在签名校验通过后、防重放校验前追加登录态校验：读取请求头
     * {@code Zone-Auth-Token} 并解析登录态，缺失或无效统一拒绝（响应与签名失败一致，
     * 不区分环节）；通过后登录上下文写入请求，业务代码经 {@code ZoneSecurityContext}
     * 取用。缺省 false 不执行登录态校验。</p>
     *
     * @return true 强制登录；缺省 false
     */
    boolean requireLogin() default false;
}
