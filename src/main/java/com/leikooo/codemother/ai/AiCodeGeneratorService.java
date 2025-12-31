package com.leikooo.codemother.ai;

import com.leikooo.codemother.ai.model.HtmlCodeResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

/**
 * @author leikooo
 */
public interface AiCodeGeneratorService {

    @SystemMessage(fromResource = "prompt/generate-html-code.md")
    HtmlCodeResult generateCode(String userMessage);

    @SystemMessage(fromResource = "prompt/generate-html-code.md")
    TokenStream generateCodeStream(String userMessage);
}
