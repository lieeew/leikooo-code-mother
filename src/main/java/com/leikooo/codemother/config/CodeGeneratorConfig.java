package com.leikooo.codemother.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * @author leikooo
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.code-generator")
public class CodeGeneratorConfig {

    private String outputDir = "generated-apps";

    private long maxFileSize = 10 * 1024 * 1024;

    private Set<String> allowedExtensions = new HashSet<>();
}
