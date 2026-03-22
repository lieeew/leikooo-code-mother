package com.leikooo.codemother.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 应用预览截图工具，支持 Vue / HTML / MultiFile 项目。
 * 仅负责启动预览服务、截图并返回文件，不做运行时错误检测。
 *
 * @author leikooo
 */
@Slf4j
public final class ScreenshotUtils {

    private static final String LOCALHOST = "127.0.0.1";
    private static final int MAX_WAIT_SECONDS = 30;
    private static final int PAGE_LOAD_TIMEOUT_MS = 15000;
    private static final String SCREENSHOT_FILENAME = "screenshot.png";

    private ScreenshotUtils() {}

    /**
     * 对指定项目目录进行预览并截图。
     *
     * @param projectPath 项目根目录（Vue 为 current，HTML/MultiFile 也为 current）
     * @param isVueProject true 使用 vite preview 服务 dist/，false 使用 serve 服务 projectPath
     * @param appId       应用 ID，仅用于日志
     * @return 截图文件，失败返回 null
     */
    public static File takeScreenshot(String projectPath, boolean isVueProject, String appId) {
        int port = findAvailablePort();
        Process serverProcess = null;
        Playwright playwright = null;
        Browser browser = null;

        try {
            serverProcess = isVueProject
                    ? startVitePreviewServer(projectPath, port)
                    : startServeServer(projectPath, port);

            if (!waitForServerReady(port)) {
                log.warn("[Screenshot] 预览服务启动超时: appId={}, port={}", appId, port);
                return null;
            }

            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            String url = String.format("http://%s:%d", LOCALHOST, port);
            page.navigate(url, new Page.NavigateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));

            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));
            } catch (TimeoutError e) {
                log.debug("[Screenshot] networkidle 超时，继续截图: appId={}", appId);
            }

            Thread.sleep(2000);

            Path screenshotPath = Paths.get(projectPath, SCREENSHOT_FILENAME);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshotPath)
                    .setFullPage(false));

            File file = screenshotPath.toFile();
            if (file.exists()) {
                log.info("[Screenshot] 截图已保存: appId={}, path={}", appId, file.getAbsolutePath());
                return file;
            }
            return null;
        } catch (Exception e) {
            log.error("[Screenshot] 截图异常: appId={}", appId, e);
            return null;
        } finally {
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception e) {
                    log.warn("[Screenshot] 关闭 browser 失败", e);
                }
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception e) {
                    log.warn("[Screenshot] 关闭 playwright 失败", e);
                }
            }
            if (serverProcess != null) {
                serverProcess.destroyForcibly();
            }
        }
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }

    private static Process startVitePreviewServer(String projectPath, int port) throws IOException {
        String command = "npx vite preview --host " + LOCALHOST + " --port " + port;
        return startProcess(projectPath, command);
    }

    private static Process startServeServer(String projectPath, int port) throws IOException {
        String command = "npx serve -l " + port;
        return startProcess(projectPath, command);
    }

    private static Process startProcess(String workingDir, String command) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static boolean waitForServerReady(int port) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        String url = "http://" + LOCALHOST + ":" + port;

        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 500) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("[Screenshot] waitForServerReady retry: url={}, attempt={}", url, i + 1);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
