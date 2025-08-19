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
import java.util.stream.Collectors;

@Service
public class OpenApiMergeService {

    private final WebClient webClient;
    private final OpenApiSourcesProps props;

    public OpenApiMergeService(WebClient.Builder builder, OpenApiSourcesProps props) {
        this.webClient = builder.build();
        this.props = props;
    }

    public Mono<OpenAPI> merge() {
        // Gọi tất cả nguồn song song (non-blocking)
        return Flux.fromIterable(props.getSources())
                .flatMap(this::fetchSpecString)
                .map(spec -> new OpenAPIV3Parser().readContents(spec, null, null).getOpenAPI())
                .filter(Objects::nonNull)
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

    private OpenAPI mergeAll(List<OpenAPI> apis) {
        OpenAPI merged = new OpenAPI();
        merged.setInfo(new Info().title("API Gateway (Merged)").version("v1"));
        merged.setPaths(new Paths());
        merged.setComponents(new Components());
        merged.setTags(new ArrayList<>());

        // de-dup tags by name
        Map<String, Tag> tagByName = new LinkedHashMap<>();

        apis.forEach(api -> {
            // paths
            if (api.getPaths() != null) {
                api.getPaths().forEach((p, item) -> merged.getPaths().addPathItem(p, item));
            }
            // tags
            if (api.getTags() != null) {
                api.getTags().forEach(t -> tagByName.putIfAbsent(t.getName(), t));
            }
            // components: merge từng phần nếu có
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
        });

        merged.setTags(new ArrayList<>(tagByName.values()));

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

