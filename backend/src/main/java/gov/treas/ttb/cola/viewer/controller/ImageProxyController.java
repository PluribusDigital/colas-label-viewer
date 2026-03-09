package gov.treas.ttb.cola.viewer.controller;

import gov.treas.ttb.cola.viewer.service.ImageProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageProxyController {

    private final ImageProxyService imageProxyService;

    @GetMapping("/{ttbId}")
    public Mono<ResponseEntity<Resource>> proxyImage(@PathVariable String ttbId) {
        return imageProxyService.proxyImage(ttbId);
    }
}
