package gov.treas.ttb.cola.viewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${ttb.socrata.base-url}")
    private String socrataBaseUrl;

    @Value("${ttb.socrata.app-token:}")
    private String appToken;

    @Bean("socrataWebClient")
    public WebClient socrataWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(socrataBaseUrl)
                .defaultHeader("X-App-Token", appToken)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    @Bean("imageWebClient")
    public WebClient imageWebClient(WebClient.Builder builder) {
        return builder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }
}
