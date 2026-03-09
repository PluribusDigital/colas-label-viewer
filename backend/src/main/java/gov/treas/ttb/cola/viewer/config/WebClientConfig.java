package gov.treas.ttb.cola.viewer.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Value("${ttb.online.base-url}")
    private String ttbOnlineBaseUrl;

    /**
     * WebClient for TTBOnline scraping. Uses JVM DNS (DefaultAddressResolverGroup)
     * instead of Netty's built-in resolver, which ignores /etc/resolv.conf and
     * fails in Docker environments. SSL verification is disabled because the
     * Alpine JRE truststore may not include the government PKI root CA —
     * acceptable for this read-only public-data demo.
     */
    @Bean("ttbOnlineWebClient")
    public WebClient ttbOnlineWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .followRedirect(true)
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to build SSL context", e);
                    }
                });
        return builder
                .baseUrl(ttbOnlineBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean("imageWebClient")
    public WebClient imageWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .followRedirect(true)
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to build SSL context", e);
                    }
                });
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }
}
