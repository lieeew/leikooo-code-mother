package com.leikooo.codemother.model.dto.request.app;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2026/1/2
 * @description
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class CreatAppRequest {

    @NotEmpty(message = "initPrompt 不能为空")
    private String initPrompt;
}
