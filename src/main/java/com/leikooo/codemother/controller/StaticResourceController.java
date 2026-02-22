package com.leikooo.codemother.controller;

import com.leikooo.codemother.constant.ResourcePathConstant;
import jakarta.servlet.http.HttpServletRequest;
import jodd.net.MimeTypes;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.springframework.http.MediaType.*;

/**
 * @author leikooo
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
            "html", MimeTypes.MIME_TEXT_HTML,
            "png", IMAGE_PNG_VALUE,
            "css", MimeTypes.MIME_TEXT_CSS,
            "js", MimeTypes.MIME_APPLICATION_JAVASCRIPT,
            "jpg", IMAGE_JPEG_VALUE
    );

    private static final String PATH = ResourcePathConstant.DEPLOY_DIR;

    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable(name = "deployKey") String deployKey,
            HttpServletRequest httpServletRequest
    ) {
        String resourcePath = httpServletRequest.getServletPath();
        resourcePath = resourcePath.substring(("/static/" + deployKey).length());
        if (resourcePath.isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Location", httpServletRequest.getRequestURI() + "/");
            return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
        }
        if ("/".equals(resourcePath)) {
            resourcePath = File.separator + "index.html";
        }
        String decodedPath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
        Path basePath = Paths.get(PATH, deployKey).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(decodedPath.replaceFirst("^/", "")).toAbsolutePath().normalize();
        if (!targetPath.startsWith(basePath)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        File file = targetPath.toFile();
        if (!file.exists()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(targetPath.toString()))
                .body(resource);
    }

    private String getContentTypeWithCharset(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf(".") + 1);
        String contentType = CONTENT_TYPE_MAP.getOrDefault(extension, APPLICATION_OCTET_STREAM_VALUE);
        if (contentType.startsWith("text/")) {
            return contentType + "; charset=" + StandardCharsets.UTF_8.name();
        }
        return contentType;
    }
}
