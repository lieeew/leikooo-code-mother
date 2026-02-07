package com.leikooo.codemother.utils;

import com.leikooo.codemother.constant.ResourcePathConstant;

import java.io.File;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/2/5
 * @description
 */
public class ProjectPathUtils {

    public static String getProjectPath(String appId) {
        return ResourcePathConstant.ROOT_PATH + File.separator + appId + File.separator + "current";
    }
}
