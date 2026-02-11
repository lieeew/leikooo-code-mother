package com.leikooo.codemother.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class FileListVO {
    private String directory;
    private List<FileInfo> files;

    @Data
    public static class FileInfo {
        private String name;
        private String path;
        private String type;
        private Long size;
        private String modifyTime;
    }
}
