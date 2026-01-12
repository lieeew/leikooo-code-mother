create database `leikooo_code_mother`;

use `leikooo_code_mother`;

-- 用户表
create table if not exists user
(
    id           BINARY(16) PRIMARY KEY comment '主键',
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userEmail    varchar(256)                           not null comment '邮箱',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- App 表
create table if not exists app
(
    id         bigint primary key auto_increment comment '主键',
    appName    varchar(256)                       null comment 'appName',
    cover      varchar(256)                       null comment '封面地址',
    initPrompt text                               not null comment '初始化 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey  varchar(64)                        null comment '部署后的唯一标识',
    userId     BINARY(16)                         not null comment '创建用户 ID',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    unique key uk_deploy (deployKey),
    key idx_userId (userId)
) comment 'App' collate utf8mb4_unicode_ci;