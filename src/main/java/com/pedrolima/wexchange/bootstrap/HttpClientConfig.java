package com.pedrolima.wexchange.bootstrap;

import com.pedrolima.wexchange.adapter.out.fiscal.FiscalClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class HttpClientConfig {

    /**
     * Redirects are never followed automatically: {@code ALWAYS} would let an
     * upstream response redirect this client anywhere, including off the
     * configured provider entirely. {@code HttpFiscalDataClient} decides for
     * itself, per issue #3, whether a redirect target is the provider's own
     * origin before ever issuing a second request to it.
     */
    @Bean
    public HttpClient httpClient(final FiscalClientProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
