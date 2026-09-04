package com.pedrolima.wexchange.adapter.out.fiscal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a response body up to a byte cap, aborting - without ever holding the
 * full body in memory - the moment the cap is crossed. This is what lets an
 * oversized response be rejected before it is fully materialized, rather than
 * buffered in full and only then discarded.
 */
final class BoundedBodyReader {

    private static final int CHUNK_SIZE = 8192;

    private BoundedBodyReader() {
    }

    static String read(final InputStream input, final long maxBytes) throws IOException {
        try (input) {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream(CHUNK_SIZE);
            final byte[] chunk = new byte[CHUNK_SIZE];
            long total = 0;
            int bytesRead;
            while ((bytesRead = input.read(chunk)) != -1) {
                total += bytesRead;
                if (total > maxBytes) {
                    throw new ResponseTooLargeException(maxBytes);
                }
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
