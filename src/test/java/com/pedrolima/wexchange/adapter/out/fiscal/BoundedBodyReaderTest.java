package com.pedrolima.wexchange.adapter.out.fiscal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedBodyReaderTest {

    @Test
    void givenABodyWithinTheCap_whenReading_thenItIsReturnedInFull() throws IOException {
        final String body = "{\"data\":[]}";
        final var input = new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

        final String result = BoundedBodyReader.read(input, 1024);

        assertEquals(body, result);
    }

    @Test
    void givenAnUnboundedStream_whenItExceedsTheCap_thenReadingStopsShortlyAfterCrossingItRatherThanDrainingTheStream() {
        final long cap = 100;
        final AtomicLong bytesRequested = new AtomicLong();
        final InputStream neverEndingStream = new InputStream() {
            @Override
            public int read() {
                bytesRequested.incrementAndGet();
                return 'x';
            }

            @Override
            public int read(final byte[] b, final int off, final int len) {
                bytesRequested.addAndGet(len);
                java.util.Arrays.fill(b, off, off + len, (byte) 'x');
                return len;
            }
        };

        assertThrows(ResponseTooLargeException.class, () -> BoundedBodyReader.read(neverEndingStream, cap));

        // Aborted after the first over-cap chunk, not after streaming megabytes: proof the
        // response was rejected before ever being fully materialized.
        assertTrue(bytesRequested.get() < cap * 100);
    }
}
