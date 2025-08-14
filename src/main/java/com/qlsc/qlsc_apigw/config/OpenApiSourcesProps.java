package com.qlsc.qlsc_apigw.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiSourcesProps {
    private List<String> sources = new ArrayList<>();

}
