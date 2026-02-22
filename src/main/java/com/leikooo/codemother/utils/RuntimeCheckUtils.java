package com.leikooo.codemother.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.io.File;

@Slf4j
public class RuntimeCheckUtils {

    private static final String LOCALHOST = "127.0.0.1";
    private static final int MAX_WAIT_SECONDS = 30;
    private static final int PAGE_LOAD_TIMEOUT_MS = 15000;

    public record RuntimeCheckResult(
            boolean hasErrors,
            List<String> consoleErrors,
            List<String> jsExceptions,
            String screenshotPath,
            String fullLog
    ) {}

    public static RuntimeCheckResult checkRuntime(String projectPath, String appId) {
        List<String> consoleErrors = new ArrayList<>();
        List<String> jsExceptions = new ArrayList<>();
        StringJoiner logJoiner = new StringJoiner(System.lineSeparator());
        logJoiner.add("=== Runtime Check Started ===");
        logJoiner.add("Project: " + projectPath);

        int port = findAvailablePort();
        logJoiner.add("Using port: " + port);

        Process previewProcess = null;
        Playwright playwright = null;
        Browser browser = null;

        try {
            previewProcess = startPreviewServer(projectPath, port);
            logJoiner.add("Preview server starting...");

            boolean ready = waitForServerReady(port);
            if (!ready) {
                logJoiner.add("ERROR: Preview server failed to start within " + MAX_WAIT_SECONDS + "s");
                return new RuntimeCheckResult(true, consoleErrors,
                        List.of("Preview server failed to start"), null, logJoiner.toString());
            }
            logJoiner.add("Preview server ready");

            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();

            page.onConsoleMessage(msg -> {
                if ("error".equals(msg.type())) {
                    String errorMsg = msg.text();
                    consoleErrors.add(errorMsg);
                    logJoiner.add("[console.error] " + errorMsg);
                }
            });

            page.onPageError(error -> {
                jsExceptions.add(error);
                logJoiner.add("[PageError] " + error);
            });

            String url = String.format("http://%s:%d", LOCALHOST, port);
            logJoiner.add("Navigating to: " + url);
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(PAGE_LOAD_TIMEOUT_MS));

            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(PAGE_LOAD_TIMEOUT_MS));
            } catch (TimeoutError e) {
                logJoiner.add("WARN: networkidle timeout, continuing with current state");
            }

            Thread.sleep(2000);

            String screenshotPath = Paths.get(projectPath, "screenshot.png").toString();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(screenshotPath))
                    .setFullPage(false));
            logJoiner.add("Screenshot saved: screenshot.png");

            boolean hasErrors = !consoleErrors.isEmpty() || !jsExceptions.isEmpty();
            logJoiner.add("=== Runtime Check Finished === errors=" + (consoleErrors.size() + jsExceptions.size()));

            return new RuntimeCheckResult(hasErrors, consoleErrors, jsExceptions, screenshotPath, logJoiner.toString());

        } catch (Exception e) {
            logJoiner.add("Exception: " + e.getMessage());
            log.error("[RuntimeCheck] 运行时检测异常: appId={}", appId, e);
            return new RuntimeCheckResult(true, consoleErrors,
                    List.of("Runtime check exception: " + e.getMessage()), null, logJoiner.toString());
        } finally {
            if (browser != null) {
                try { browser.close(); } catch (Exception e) { log.warn("Browser close error", e); }
            }
            if (playwright != null) {
                try { playwright.close(); } catch (Exception e) { log.warn("Playwright close error", e); }
            }
            if (previewProcess != null) {
                previewProcess.destroyForcibly();
                logJoiner.add("Preview server stopped");
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

    private static Process startPreviewServer(String projectPath, int port) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        String command = "npx vite preview --host " + LOCALHOST + " --port " + port;
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        pb.directory(new File(projectPath));
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static boolean waitForServerReady(int port) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
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
                log.debug("waitForServerReady retry: url={}, attempt={}, error={}", url, i + 1, e.getMessage());
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
