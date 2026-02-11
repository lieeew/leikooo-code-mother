package com.leikooo.codemother.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FileTreeNodeVO implements Serializable {
    private String id;
    private String name;
    private String type;
    private String path;
    private Long size;
    private String extension;
    private List<FileTreeNodeVO> children;
}
