package com.pedrolima.wexchange.adapter.out.fiscal;

/** A successful (2xx) upstream response, body already bounded and materialized. */
record RawResponse(int statusCode, String body) {
}
