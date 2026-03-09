package gov.treas.ttb.cola.viewer.service;

import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class ImageProxyService {

    private final WebClient imageWebClient;
    private final ColaRepository colaRepository;

    public ImageProxyService(
            @Qualifier("imageWebClient") WebClient imageWebClient,
            ColaRepository colaRepository) {
        this.imageWebClient = imageWebClient;
        this.colaRepository = colaRepository;
    }

    public Mono<ResponseEntity<Resource>> proxyImage(String ttbId) {
        return Mono.fromCallable(() -> colaRepository.findByTtbId(ttbId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> {
                    if (opt.isEmpty() || opt.get().getLabelImageUrl() == null) {
                        return Mono.just(ResponseEntity.notFound().<Resource>build());
                    }
                    String url = opt.get().getLabelImageUrl();
                    return imageWebClient.get()
                            .uri(url)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, resp -> {
                                log.warn("TTB image server returned {} for {}", resp.statusCode(), url);
                                return Mono.error(new ImageFetchException(url, resp.statusCode().value()));
                            })
                            .bodyToMono(byte[].class)
                            .map(bytes -> ResponseEntity.ok()
                                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                                    .contentType(MediaType.IMAGE_JPEG)
                                    .body((Resource) new ByteArrayResource(bytes)))
                            .onErrorReturn(
                                    ImageFetchException.class,
                                    ResponseEntity.status(HttpStatus.BAD_GATEWAY).<Resource>build()
                            );
                });
    }

    public static class ImageFetchException extends RuntimeException {
        public ImageFetchException(String url, int statusCode) {
            super("Failed to fetch image from " + url + " status=" + statusCode);
        }
    }
}
