package com.leikooo.codemother.utils;

import com.leikooo.codemother.exception.BusinessException;
import com.leikooo.codemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * @author leikooo
 */
@Slf4j
public class VueBuildUtils {

    private static final String NPM_REGISTRY = "https://registry.npmmirror.com";

    /**
     * 安装依赖 & build
     * @param projectPath path
     */
    public static void buildVueProject(String projectPath) {
        checkNodeAvailable();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "cd /d " + projectPath + " && npm config set registry " + NPM_REGISTRY + " && npm install && npm run build");
            } else {
                pb = new ProcessBuilder("bash", "-c", "cd " + projectPath + " && npm config set registry " + NPM_REGISTRY + " && npm install && npm run build");
            }
            Process process = pb.inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Vue build failed with exit code: {}", exitCode);
            }
        } catch (IOException e) {
            log.error("Vue build IO exception: {}", e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            log.error("Vue build interrupted: {}", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
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
}