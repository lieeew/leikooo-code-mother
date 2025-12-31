package com.leikooo.codemother.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Getter;
import lombok.Setter;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/29
 * @description
 */
@Setter
@Getter
public class HtmlCodeResult {

    @Description("生成 html 代码的描述")
    private String describe;

    @Description("生成的 html 代码")
    private String code;
}
