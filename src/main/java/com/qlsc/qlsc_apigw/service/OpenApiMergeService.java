package com.qlsc.qlsc_apigw.service;

// OpenApiMergeService.java

import com.qlsc.qlsc_apigw.config.OpenApiSourcesProps;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class OpenApiMergeService {

    private final WebClient webClient;
    private final OpenApiSourcesProps props;

    private static final Map<String, String> urlPrefixMap = Map.of(
            "http://localhost:8081", "/user",
            "http://localhost:8082", "/court-management",
            "http://localhost:8083", "/booking"
    );

    public OpenApiMergeService(WebClient.Builder builder, OpenApiSourcesProps props) {
        this.webClient = builder.build();
        this.props = props;
    }

    public Mono<OpenAPI> merge() {
        // Gọi tất cả nguồn song song (non-blocking)
//        return Flux.fromIterable(props.getSources())
//                .flatMap(this::fetchSpecString)
//                .map(spec -> new OpenAPIV3Parser().readContents(spec, null, null).getOpenAPI())
//                .filter(Objects::nonNull)
//                .collectList()
//                .map(this::mergeAll);
        return Flux.fromIterable(props.getSources())
                .flatMap(url -> fetchSpecString(url)
                        .map(spec -> Map.entry(url, new OpenAPIV3Parser().readContents(spec, null, null).getOpenAPI()))
                )
                .filter(entry -> entry.getValue() != null)
                .collectList()
                .map(this::mergeAll);
    }

    private Mono<String> fetchSpecString(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(ex -> {
                    System.err.println("Failed to fetch " + url + ": " + ex.getMessage());
                    return Mono.empty();
                });
    }

    private OpenAPI mergeAll(List<Map.Entry<String, OpenAPI>> entries) {
        OpenAPI merged = new OpenAPI()
                .info(new Info().title("API Gateway (Merged) By Htruong48").version("v1"))
                .paths(new Paths())
                .components(new Components())
                .tags(new ArrayList<>());

        Map<String, Tag> tagByName = new LinkedHashMap<>();

        for (Map.Entry<String, OpenAPI> entry : entries) {
            String sourceUrl = entry.getKey();
            OpenAPI api = entry.getValue();
            String prefix = urlPrefixMap.entrySet().stream()
                    .filter(e -> sourceUrl.startsWith(e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("");

            if (api.getPaths() != null) {
                api.getPaths().forEach((path, item) -> {
                    // thêm prefix vào path
                    String newPath = prefix + path;
                    merged.getPaths().addPathItem(newPath, item);
                });
            }

            if (api.getTags() != null) {
                api.getTags().forEach(t -> tagByName.putIfAbsent(t.getName(), t));
            }

            if (api.getComponents() != null) {
                Components src = api.getComponents();
                Components dst = merged.getComponents();
                if (src.getSchemas() != null) {
                    if (dst.getSchemas() == null) dst.setSchemas(new LinkedHashMap<>());
                    dst.getSchemas().putAll(src.getSchemas());
                }
                if (src.getSecuritySchemes() != null) {
                    if (dst.getSecuritySchemes() == null) dst.setSecuritySchemes(new LinkedHashMap<>());
                    dst.getSecuritySchemes().putAll(src.getSecuritySchemes());
                }
                if (src.getParameters() != null) {
                    if (dst.getParameters() == null) dst.setParameters(new LinkedHashMap<>());
                    dst.getParameters().putAll(src.getParameters());
                }
                if (src.getResponses() != null) {
                    if (dst.getResponses() == null) dst.setResponses(new LinkedHashMap<>());
                    dst.getResponses().putAll(src.getResponses());
                }
                if (src.getRequestBodies() != null) {
                    if (dst.getRequestBodies() == null) dst.setRequestBodies(new LinkedHashMap<>());
                    dst.getRequestBodies().putAll(src.getRequestBodies());
                }
                if (src.getHeaders() != null) {
                    if (dst.getHeaders() == null) dst.setHeaders(new LinkedHashMap<>());
                    dst.getHeaders().putAll(src.getHeaders());
                }
                if (src.getLinks() != null) {
                    if (dst.getLinks() == null) dst.setLinks(new LinkedHashMap<>());
                    dst.getLinks().putAll(src.getLinks());
                }
                if (src.getCallbacks() != null) {
                    if (dst.getCallbacks() == null) dst.setCallbacks(new LinkedHashMap<>());
                    dst.getCallbacks().putAll(src.getCallbacks());
                }
            }
        }

        merged.setTags(new ArrayList<>(tagByName.values()));

        // thêm security schema như cũ
        // ...
        // ✅ Thêm Bearer Token một lần duy nhất ở đây
        final String securitySchemeName = "bearerAuth";
        merged.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(securitySchemeName));
        merged.getComponents().addSecuritySchemes(securitySchemeName,
                new io.swagger.v3.oas.models.security.SecurityScheme()
                        .name(securitySchemeName)
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
        return merged;
    }
}

