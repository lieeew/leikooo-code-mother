package com.leikooo.codemother.model.vo;

import lombok.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/25
 * @description 验证码验证返回
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class VerifyCodeVO {
    private String token;
    private String email;
}
