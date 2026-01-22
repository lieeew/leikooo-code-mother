package com.leikooo.codemother.model.vo;

import cn.hutool.core.bean.BeanUtil;
import com.leikooo.codemother.model.entity.User;
import com.leikooo.codemother.utils.UuidV7Generator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Optional;

/**
 * @author <a href="https://github.com/lieeew">leikooo</a>
 * @date 2025/12/23
 * @description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    /**
     * id
     */
    private String id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    public static UserVO toVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        Optional.ofNullable(user).map(User::getId)
                .ifPresent(userId -> userVO.setId(UuidV7Generator.bytesToUuid(user.getId())));
        return userVO;
    }
}
