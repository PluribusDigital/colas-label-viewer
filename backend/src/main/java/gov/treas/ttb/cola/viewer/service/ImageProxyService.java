package gov.treas.ttb.cola.viewer.service;

import gov.treas.ttb.cola.viewer.repository.ColaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ImageProxyService {

    static final String PRINTABLE_FORM  = "/colasonline/viewColaDetails.do?action=publicFormDisplay&ttbid=";
    static final String SEARCH_PAGE     = "/colasonline/publicSearchColasBasic.do";
    static final Pattern ATTACHMENT_URL = Pattern.compile(
            "src=\"(/colasonline/publicViewAttachment\\.do\\?filename=[^\"]+&filetype=l)\"");

    private final WebClient ttbClient;
    private final ColaRepository colaRepository;

    public ImageProxyService(
            @Qualifier("ttbOnlineWebClient") WebClient ttbClient,
            ColaRepository colaRepository) {
        this.ttbClient = ttbClient;
        this.colaRepository = colaRepository;
    }

    public Mono<ResponseEntity<Resource>> proxyImage(String ttbId) {
        return Mono.fromCallable(() -> colaRepository.findByTtbId(ttbId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Mono.just(ResponseEntity.notFound().<Resource>build());
                    }
                    String cachedUrl = opt.get().getLabelImageUrl();
                    // TTBOnline requires the COLA details form to be loaded in the SAME
                    // session before it will serve the attachment file. So we always:
                    //   1. GET search page → session cookie
                    //   2. GET form page → authorizes attachment access + gives us the URL
                    //   3. GET attachment → actual image bytes
                    return establishSession()
                            .flatMap(cookieHeader -> loadFormAndGetAttachmentUrl(ttbId, cookieHeader, cachedUrl)
                                    .flatMap(attachUrl -> fetchAndProxy(attachUrl, cookieHeader, ttbId))
                                    .switchIfEmpty(Mono.just(ResponseEntity.notFound().<Resource>build())));
                });
    }

    /** GETs the search page and returns the session cookie header string. */
    private Mono<String> establishSession() {
        return ttbClient.get()
                .uri(SEARCH_PAGE)
                .retrieve()
                .toEntity(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(resp -> cookieString(parseCookies(resp)))
                .onErrorReturn("");
    }

    /**
     * GETs the printable COLA form (required to authorise the subsequent attachment fetch),
     * returns the attachment URL (from cache if available, otherwise extracted from the HTML).
     */
    Mono<String> loadFormAndGetAttachmentUrl(String ttbId, String cookieHeader, String cachedUrl) {
        return ttbClient.get()
                .uri(PRINTABLE_FORM + ttbId)
                .header("Cookie", cookieHeader)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .mapNotNull(html -> {
                    // Use cached URL if we have it — just needed the form page for session auth
                    if (cachedUrl != null) return cachedUrl;
                    Matcher m = ATTACHMENT_URL.matcher(html);
                    if (!m.find()) {
                        log.warn("No label attachment found for ttbId={}", ttbId);
                        return null;
                    }
                    String url = "https://www.ttbonline.gov" + m.group(1);
                    // Persist asynchronously
                    Mono.fromRunnable(() -> persistImageUrl(ttbId, url))
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();
                    return url;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to load form for ttbId={}: {}", ttbId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<ResponseEntity<Resource>> fetchAndProxy(String url, String cookieHeader, String ttbId) {
        return ttbClient.get()
                .uri(url)
                .header("Cookie", cookieHeader)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(20))
                .map(bytes -> {
                    String contentType = guessContentType(url);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body((Resource) new ByteArrayResource(bytes));
                })
                .onErrorResume(e -> {
                    log.warn("Image fetch failed for ttbId={} url={}: {}", ttbId, url, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).<Resource>build());
                });
    }

    private static Map<String, String> parseCookies(ResponseEntity<?> response) {
        Map<String, String> map = new LinkedHashMap<>();
        if (response == null) return map;
        List<String> setCookies = response.getHeaders().get("Set-Cookie");
        if (setCookies == null) return map;
        for (String raw : setCookies) {
            String nameValue = raw.split(";")[0].strip();
            int eq = nameValue.indexOf('=');
            if (eq > 0) map.put(nameValue.substring(0, eq), nameValue.substring(eq + 1));
        }
        return map;
    }

    private static String cookieString(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        cookies.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    @Transactional
    void persistImageUrl(String ttbId, String url) {
        colaRepository.findByTtbId(ttbId).ifPresent(r -> {
            r.setLabelImageUrl(url);
            colaRepository.save(r);
        });
    }

    private static String guessContentType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".tif")) return "image/tiff";
        return "image/jpeg";
    }

    public static class ImageFetchException extends RuntimeException {
        public ImageFetchException(String url, int statusCode) {
            super("Failed to fetch image from " + url + " status=" + statusCode);
        }
    }
}
