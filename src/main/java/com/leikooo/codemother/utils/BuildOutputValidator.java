package com.leikooo.codemother.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class BuildOutputValidator {

    private static final long MIN_INDEX_SIZE = 500;
    private static final long MIN_JS_BUNDLE_SIZE = 10 * 1024;

    public record ValidationResult(
            boolean valid,
            List<String> errors,
            String summary
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), "Build output validation passed");
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, String.join("\n", errors));
        }

        public static ValidationResult validateVueBuild(String projectPath) {
            List<String> errors = new ArrayList<>();
            Path distPath = Paths.get(projectPath, "dist");

            if (!Files.exists(distPath) || !Files.isDirectory(distPath)) {
                errors.add("❌ dist/ directory not found");
                return ValidationResult.failure(errors);
            }

            try (Stream<Path> files = Files.list(distPath)) {
                if (files.findAny().isEmpty()) {
                    errors.add("❌ dist/ directory is empty");
                    return ValidationResult.failure(errors);
                }
            } catch (IOException e) {
                errors.add("❌ Failed to read dist/ directory: " + e.getMessage());
                return ValidationResult.failure(errors);
            }

            Path indexPath = distPath.resolve("index.html");
            if (!Files.exists(indexPath)) {
                errors.add("❌ dist/index.html not found");
            } else {
                try {
                    long size = Files.size(indexPath);
                    if (size < MIN_INDEX_SIZE) {
                        errors.add("❌ index.html too small (" + size + " bytes, expected >" + MIN_INDEX_SIZE + ")");
                    } else {
                        String content = Files.readString(indexPath);
                        if (!content.contains("<div id=\"app\">") && !content.contains("<div id=\"root\">")) {
                            errors.add("❌ index.html missing Vue mount point (<div id=\"app\"> or <div id=\"root\">)");
                        }
                    }
                } catch (IOException e) {
                    errors.add("❌ Failed to read index.html: " + e.getMessage());
                }
            }

            Path assetsPath = distPath.resolve("assets");
            if (!Files.exists(assetsPath) || !Files.isDirectory(assetsPath)) {
                errors.add("❌ dist/assets/ directory not found");
            } else {
                try (Stream<Path> jsFiles = Files.walk(assetsPath)
                        .filter(p -> p.toString().endsWith(".js"))) {
                    List<Path> jsList = jsFiles.toList();
                    if (jsList.isEmpty()) {
                        errors.add("❌ No JavaScript files found in dist/assets/");
                    } else {
                        long maxSize = jsList.stream()
                                .mapToLong(p -> {
                                    try {
                                        return Files.size(p);
                                    } catch (IOException e) {
                                        return 0;
                                    }
                                })
                                .max()
                                .orElse(0);

                        if (maxSize < MIN_JS_BUNDLE_SIZE) {
                            errors.add("❌ Largest JS bundle too small (" + maxSize + " bytes, expected >" + MIN_JS_BUNDLE_SIZE + ")");
                        }
                    }
                } catch (IOException e) {
                    errors.add("❌ Failed to scan assets/ directory: " + e.getMessage());
                }
            }

            return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
        }
    }
}
