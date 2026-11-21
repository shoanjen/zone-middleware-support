package com.zone.middleware.security.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 请求体可缓存包装器：首次读取透传并缓存（惰性），后续读取回放缓存。
 *
 * <p>与 {@code ContentCachingRequestWrapper}（仅供观测、不支持二次读取）不同，本包装器
 * 保证拦截器消费 body 后 Controller 仍可通过 {@code getInputStream()} 完整读取。</p>
 *
 * <p>行为边界：</p>
 * <ul>
 *   <li>惰性：包装本身不读 body，首次消费才边读边缓存——未标注端点的请求读放行为
 *       与原始请求完全一致；</li>
 *   <li>超限：缓存上限 {@value #MAX_CACHED_BYTES} 字节，超出即停止缓存并置溢出标记
 *       （{@link #isBodyOverflowed()}），后续内容原样透传不截断——标注端点由拦截器
 *       据溢出标记拒绝，未标注端点行为不变；</li>
 *   <li>溢出后不提供回放（标注端点已被拦截器拒绝，不会走到 Controller）。</li>
 * </ul>
 */
public class ZoneCachedBodyRequestWrapper extends HttpServletRequestWrapper {

    /** 缓存上限（字节） */
    static final int MAX_CACHED_BYTES = 1024 * 1024;

    /** 首次输入流（边读边缓存，记忆化防止重复创建） */
    private CachingServletInputStream firstInputStream;

    /** body 是否已被首次消费 */
    private boolean bodyConsumed;

    /** 是否超出缓存上限（超出部分未缓存） */
    private boolean bodyOverflowed;

    /** 完整缓存内容（未溢出时才有值） */
    private byte[] cachedBody;

    /**
     * 构建可缓存包装器。
     *
     * @param request 原始请求
     */
    public ZoneCachedBodyRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    /**
     * 首次调用返回「边读边缓存」输入流；body 已消费且未溢出时返回缓存回放流。
     *
     * @return 请求体输入流
     * @throws IOException 底层流读取失败
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (bodyConsumed) {
            if (bodyOverflowed || cachedBody == null) {
                throw new IllegalStateException("请求体超出缓存上限，不可回放");
            }
            return new ReplayServletInputStream(cachedBody);
        }
        bodyConsumed = true;
        firstInputStream = new CachingServletInputStream(super.getInputStream(), this);
        return firstInputStream;
    }

    /**
     * 字符读取器（经 {@link #getInputStream()} 路由，保证缓存与回放一致）。
     *
     * @return 请求体字符读取器
     * @throws IOException 底层流读取失败
     */
    @Override
    public BufferedReader getReader() throws IOException {
        Charset charset = getCharacterEncoding() != null
                ? Charset.forName(getCharacterEncoding()) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /**
     * 一次性消费请求体（走缓存输入流，供拦截器读取签名参数）。
     *
     * @return 完整 body 字节（可能超出缓存上限，超出部分仍在返回值中）
     * @throws IOException 读取失败
     */
    public byte[] consumeBody() throws IOException {
        return StreamUtils.copyToByteArray(getInputStream());
    }

    /**
     * 请求体是否超出缓存上限。
     *
     * @return true 表示已溢出（不可回放）
     */
    public boolean isBodyOverflowed() {
        return bodyOverflowed;
    }

    /**
     * 边读边缓存输入流：缓存至字节上限，溢出后停止缓存、原样透传不截断。
     */
    private static class CachingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final ZoneCachedBodyRequestWrapper wrapper;
        private final ByteArrayOutputStream cache = new ByteArrayOutputStream();

        /**
         * 构建缓存输入流。
         *
         * @param delegate 底层输入流
         * @param wrapper  所属包装器（回写缓存与溢出标记）
         */
        CachingServletInputStream(ServletInputStream delegate, ZoneCachedBodyRequestWrapper wrapper) {
            this.delegate = delegate;
            this.wrapper = wrapper;
        }

        /**
         * 逐字节读取并写入缓存（超限停止缓存）。
         *
         * @return 字节值；流结束返回 -1
         * @throws IOException 底层读取失败
         */
        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                if (cache.size() < MAX_CACHED_BYTES) {
                    cache.write(b);
                } else {
                    wrapper.bodyOverflowed = true;
                }
            } else if (!wrapper.bodyOverflowed) {
                wrapper.cachedBody = cache.toByteArray();
            }
            return b;
        }

        /**
         * 批量读取并逐字节入缓存。
         *
         * @param b   目标数组
         * @param off 偏移
         * @param len 长度
         * @return 实际读取字节数；流结束返回 -1
         * @throws IOException 底层读取失败
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read != -1) {
                for (int i = off; i < off + read; i++) {
                    if (cache.size() < MAX_CACHED_BYTES) {
                        cache.write(b[i]);
                    } else {
                        wrapper.bodyOverflowed = true;
                        break;
                    }
                }
            } else if (!wrapper.bodyOverflowed) {
                wrapper.cachedBody = cache.toByteArray();
            }
            return read;
        }

        /**
         * 是否已读到流末尾。
         *
         * @return 底层流结束后 true
         */
        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        /**
         * 是否可无阻塞读取。
         *
         * @return 底层流可读即 true
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 非异步流不支持读监听。
         *
         * @param readListener 监听器（忽略）
         */
        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("缓存输入流不支持异步读监听");
        }
    }

    /**
     * 缓存回放输入流。
     */
    private static class ReplayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        /**
         * 构建回放流。
         *
         * @param cachedBody 缓存的完整 body
         */
        ReplayServletInputStream(byte[] cachedBody) {
            this.buffer = new ByteArrayInputStream(cachedBody);
        }

        /**
         * 读取下一字节。
         *
         * @return 字节值；流结束返回 -1
         */
        @Override
        public int read() {
            return buffer.read();
        }

        /**
         * 是否已读完。
         *
         * @return 缓冲区耗尽后 true
         */
        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        /**
         * 是否可无阻塞读取。
         *
         * @return 有剩余字节即 true
         */
        @Override
        public boolean isReady() {
            return buffer.available() > 0;
        }

        /**
         * 非异步流不支持读监听。
         *
         * @param readListener 监听器（忽略）
         */
        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("回放输入流不支持异步读监听");
        }
    }
}
