package com.leikooo.codemother.model.vo;

import lombok.Data;

@Data
public class FileContentVO {
    private String filePath;
    private String content;
    private Integer totalLines;
    private Integer returnedLines;
    private String language;
    private String encoding;
    private Boolean hasMore;
}
