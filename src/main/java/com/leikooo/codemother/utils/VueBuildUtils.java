package com.leikooo.codemother.utils;

import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/**
 * @author leikooo
 */
@Slf4j
public class VueBuildUtils {

    private static final String NPM_REGISTRY = "https://registry.npmmirror.com";

    /**
     * 安装依赖 & build，返回完整日志
     * @param projectPath path
     * @return BuildResult
     */
    public static BuildResult buildVueProject(String projectPath) {
        checkNodeAvailable();
        StringJoiner logJoiner = new StringJoiner(System.lineSeparator());
        logJoiner.add("=== Vue Build Started ===");
        logJoiner.add("Project: " + projectPath);

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "chcp 65001 > null && cd /d " + projectPath + " && npm config set registry " + NPM_REGISTRY + " && npm install && npm run build");
            } else {
                pb = new ProcessBuilder("bash", "-c", "export LANG=en_US.UTF-8 && export LC_ALL=en_US.UTF-8 && cd " + projectPath + " && npm config set registry " + NPM_REGISTRY + " && npm install && npm run build");
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logJoiner.add(line);
                }
            }

            int exitCode = process.waitFor();
            logJoiner.add("=== Build Exit Code: " + exitCode + " ===");

            boolean success = exitCode == 0;
            return new BuildResult(success, logJoiner.toString(), exitCode);

        } catch (IOException e) {
            logJoiner.add("IO Exception: " + e.getMessage());
            log.error("Vue build IO exception: {}", e);
            return new BuildResult(false, logJoiner.toString(), -1);
        } catch (InterruptedException e) {
            logJoiner.add("Interrupted: " + e.getMessage());
            log.error("Vue build interrupted: {}", e);
            Thread.currentThread().interrupt();
            return new BuildResult(false, logJoiner.toString(), -1);
        }
    }

    private static void checkNodeAvailable() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb = os.contains("win")
                    ? new ProcessBuilder("cmd", "/c", "node -v")
                    : new ProcessBuilder("bash", "-c", "node -v");
            Process process = pb.redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Node.js is not installed or not found in PATH");
            }
        } catch (IOException e) {
            log.error("Check node IO exception: {}", e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            log.error("Check node interrupted: {}", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public record BuildResult(
            boolean success,
            String fullLog,
            int exitCode
    ) {
    }
}