# Code Mother

AI 驱动的代码生成平台。

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.4-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-1.1.2-0A0A0A?logo=spring&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-7.0-646CFF?logo=vite&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

## 版本信息

- **后端版本**：`0.0.1-SNAPSHOT`（`pom.xml`）
- **前端版本**：`0.0.0`（`code-mother-fronted/package.json`）
- **最后同步时间**：`2026-04-08`

## 项目亮点

- **三种代码生成模式**：单 HTML、多文件静态页面、完整 Vue 项目脚手架
- **AI 工具调用可观测**：记录工具调用前后链路，支持排障与回放分析
- **自动化错误修复流程**：构建失败后可触发 AI 修复，减少手工排查成本
- **代码版本可追溯**：支持生成/修复版本留痕、对比与回滚
- **生成即预览**：产物构建后可快速进入预览，缩短“生成到验证”闭环

## 功能特性

- **多模式代码生成**：支持单 HTML 文件、多文件静态页面、Vue 项目三种生成模式
- **工具调用追踪**：完整记录 AI 工具调用过程，支持调用前和调用后两个阶段的数据持久化
- **对话历史管理**：保存多轮对话上下文，支持会话历史查询
- **Vue 项目脚手架**：自动生成 Vue 3 项目代码，包含路由配置、组件架构
- **智能错误修复**：自动检测 Vue 构建错误，AI 分析并修复代码问题
- **可观测性记录**：记录 Token 消耗、API 耗时，支持成本分析
- **精选应用管理**：支持应用优先级设置，高优先级应用可缓存加速
- **代码版本管理**：保存每次生成/修复的代码版本，支持版本回滚、对比、追溯
- **构建产物预览**：生成后自动启动预览服务，提供访问链接

## 待开发功能

### 1. 提示词优化助手（中优先级）
- **功能**：分析用户 prompt，提供优化建议
- **价值**：提升生成代码质量
- **实现思路**：
  - AI 分析 prompt 的完整性和清晰度
  - 生成优化后的 prompt 建议
  - 保存优质 prompt 模板

### 2. 代码质量分析（低优先级）
- **功能**：分析生成代码的质量指标
- **价值**：提升输出代码的可维护性
- **实现思路**：
  - 集成代码复杂度分析
  - 安全漏洞扫描
  - 生成质量报告

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.4 |
| AI 框架 | Spring AI 1.1.2 |
| 前端框架 | Vue 3.5 + Vite 7 |
| Java | JDK 21 |
| 数据库 | MySQL + MyBatis-Plus |
| 缓存 | Redis + Caffeine |
| 认证 | Sa-Token |
| 对象存储 |腾讯云 COS |

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis 6.0+

### 启动服务

```bash
# 1. 导入数据库脚本
mysql -u root -p < sql/create_table_sql.sql

# 2. 配置 Redis 和数据库连接
# 修改 src/main/resources/application.yml

# 3. 启动后端服务
cd code-mother
mvn spring-boot:run

# 4. 启动前端服务
cd code-mother-fronted
npm install
npm run dev
```

## 项目结构

```
code-mother/
├── src/main/java/com/leikooo/codemother/
│   ├── ai/               # AI 相关（ChatClient、工具、提示词）
│   ├── aop/              # AOP 切面（工具调用记录）
│   ├── controller/       # 控制器
│   ├── service/          # 服务层
│   ├── mapper/           # MyBatis Mapper
│   ├── model/            # 实体、枚举、VO
│   └── utils/            # 工具类
├── src/main/resources/
│   ├── prompt/           # 代码生成提示词
│   └── mapper/           # Mapper XML
└── sql/                  # 数据库脚本
```

## 代码生成模式

| 模式 | 说明 |
|------|------|
| HTML | 单文件生成，CSS/JS 内嵌 |
| Multi-file | 多文件分离（HTML/CSS/JS） |
| Vue Project | 完整 Vue 3 项目脚手架 |

## License

MIT
