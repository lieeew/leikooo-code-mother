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
    id          bigint primary key auto_increment comment '主键',
    appName     varchar(256)                       null comment 'appName',
    cover       varchar(256)                       null comment '封面地址',
    initPrompt  text                               not null comment '初始化 prompt',
    codeGenType varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey   varchar(64)                        null comment '部署后的唯一标识',
    priority    INT      DEFAULT 0 COMMENT '优先级',
    userId      BINARY(16)                         not null comment '创建用户 ID',
    editTime    datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    unique key uk_deploy (deployKey),
    key idx_userId (userId),
    key idx_priority (priority)
) comment 'App' collate utf8mb4_unicode_ci;

-- Tool Call 记录表
create table if not exists tool_call_record
(
    id         bigint primary key auto_increment comment '主键',
    sessionId  varchar(64)                        not null comment '会话 ID',
    toolCallId varchar(64)                        not null comment '工具调用 ID',
    className  varchar(256)                       not null comment '工具类名',
    methodName varchar(256)                       not null comment '工具方法名',
    callType   varchar(32)                        not null comment '调用类型（call/result）',
    result     text                               null comment '工具调用结果',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    key idx_sess4ionId (sessionId),
    key idx_toolCallId (toolCallId)
) comment '工具调用记录' collate utf8mb4_unicode_ci;

-- Spring AI Chat Memory 表
create table if not exists spring_ai_chat_memory
(
    conversation_id varchar(36)                                  not null comment '会话 ID',
    content         longtext                                     not null comment '消息内容',
    type            enum ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') not null comment '消息类型',
    timestamp       timestamp                                    not null comment '时间戳',
    key idx_conversation_timestamp (conversation_id, timestamp)
) comment 'Spring AI 聊天记忆' collate utf8mb4_unicode_ci;

-- 可观测性记录表
create table if not exists observable_record
(
    id              bigint primary key auto_increment comment '主键',
    conversation_id varchar(36)                         null comment '会话 ID',
    input_tokens    bigint                              null comment '输入 token 数',
    output_tokens   bigint                              null comment '输出 token 数',
    duration_ms     bigint                              null comment '耗时（毫秒）',
    tool_call_count int                                 null comment '工具调用次数',
    timestamp       timestamp default CURRENT_TIMESTAMP not null comment '时间戳',
    key idx_conversation_id (conversation_id)
) comment '可观测性记录' collate utf8mb4_unicode_ci;