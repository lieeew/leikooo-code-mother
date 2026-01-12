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
import java.nio.charset.StandardCharsets;
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

    private static final String PATH = ResourcePathConstant.ROOT_PATH;

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
        String finalPath = PATH + File.separator + deployKey + resourcePath;
        File file = new File(finalPath);
        if (!file.exists()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(finalPath))
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
