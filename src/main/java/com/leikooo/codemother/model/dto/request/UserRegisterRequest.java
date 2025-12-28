package com.leikooo.codemother.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/23
 * @description
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class UserRegisterRequest {

    @NotBlank(message = "用户名不能为空")
    private String userName;

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度为4-20位")
    private String userAccount;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20位")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String userEmail;
}
