package com.fantasy.bff.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Counts the bytes actually read from a request body, so that a body which stops arriving
 * mid-stream can be reported with how far it got (see the client-abort branch in
 * {@link com.fantasy.bff.exception.GlobalExceptionHandler}).
 *
 * <p>Saved projections were failing in production with the upload dying part-way through, and
 * the exception alone cannot tell a body truncated at a fixed size (a proxy cap) from one that
 * simply stopped arriving (a stalled or timed-out connection). Comparing bytes read against the
 * declared {@code Content-Length} across occurrences answers that.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyByteCountFilter extends OncePerRequestFilter {

    public static final String BYTES_READ_ATTRIBUTE = "com.fantasy.bff.bytesRead";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!hasBody(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        CountingRequest counting = new CountingRequest(request);
        request.setAttribute(BYTES_READ_ATTRIBUTE, counting);
        filterChain.doFilter(counting, response);
    }

    private static boolean hasBody(HttpServletRequest request) {
        if (request.getContentLengthLong() == 0) {
            return false;
        }
        String method = request.getMethod();
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /** Bytes read so far from the request body, or -1 when the body was never wrapped. */
    public static long bytesRead(HttpServletRequest request) {
        Object attribute = request.getAttribute(BYTES_READ_ATTRIBUTE);
        return attribute instanceof CountingRequest counting ? counting.bytesRead() : -1;
    }

    private static final class CountingRequest extends HttpServletRequestWrapper {

        private CountingInputStream inputStream;

        private CountingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new CountingInputStream(super.getInputStream());
            }
            return inputStream;
        }

        private long bytesRead() {
            return inputStream == null ? 0 : inputStream.count;
        }
    }

    private static final class CountingInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        private CountingInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int read = delegate.read();
            if (read != -1) {
                count++;
            }
            return read;
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
