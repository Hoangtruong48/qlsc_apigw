package com.qlsc.qlsc_apigw.controller;
import com.qlsc.qlsc_apigw.service.OpenApiMergeService;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class OpenApiMergeController {

    private final OpenApiMergeService mergeService;

    @Autowired
    public OpenApiMergeController(OpenApiMergeService mergeService) {
        this.mergeService = mergeService;
    }

    @GetMapping(value = "/v3/api-docs-merged", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> merged() {
        return mergeService.merge()
                .map(openAPI -> {
                    try {
//                        // Thêm securitySchemes
//                        final String securitySchemeName = "bearerAuth";
//                        openAPI.addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
//                        openAPI.components(new Components()
//                                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
//                                        .name(securitySchemeName)
//                                        .type(SecurityScheme.Type.HTTP)
//                                        .scheme("bearer")
//                                        .bearerFormat("JWT")));
                        // serialize OpenAPI -> JSON string
                        return Json.mapper().writeValueAsString(openAPI);
                    } catch (Exception e) {
                        throw new RuntimeException("Serialize merged OpenAPI failed", e);
                    }
                });
    }
}
