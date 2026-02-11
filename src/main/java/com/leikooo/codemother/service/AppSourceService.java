package com.leikooo.codemother.service;

import com.leikooo.codemother.model.vo.FileContentVO;
import com.leikooo.codemother.model.vo.FileListVO;
import com.leikooo.codemother.model.vo.FileTreeNodeVO;

/**
 * @author leikooo
 */
public interface AppSourceService {
    FileTreeNodeVO getFileTree(Long appId);

    FileContentVO getFileContent(Long appId, String filePath, Integer start, Integer limit);

    FileListVO getFileList(Long appId, String directory, Boolean recursive);
}
