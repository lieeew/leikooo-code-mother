package com.leikooo.codemother.model.dto;

import com.leikooo.codemother.model.dto.request.app.CreatAppRequest;
import com.leikooo.codemother.model.vo.UserVO;
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
public class CreatAppDto {
    /**
     * 创建 App 的提示词
     */
    private CreatAppRequest creatAppRequest;

    /**
     * 登录的用户信息
     */
    private UserVO loginUser;
}
