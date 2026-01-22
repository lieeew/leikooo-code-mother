# Code Mother

AI 驱动的代码生成平台。

## 功能特性

- **多模式代码生成**：支持单 HTML 文件、多文件静态页面、Vue 项目三种生成模式
- **工具调用追踪**：完整记录 AI 工具调用过程，支持调用前和调用后两个阶段的数据持久化
- **对话历史管理**：保存多轮对话上下文，支持会话历史查询
- **Vue 项目脚手架**：自动生成 Vue 3 项目代码，包含路由配置、组件架构
- **智能错误修复**：自动检测 Vue 构建错误，AI 分析并修复代码问题
- **可观测性记录**：记录 Token 消耗、API 耗时，支持成本分析
- **精选应用管理**：支持应用优先级设置，高优先级应用可缓存加速

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.x |
| AI 框架 | Spring AI |
| 前端框架 | Vue 3 + Vite |
| 数据库 | MySQL + MyBatis-Plus |
| 缓存 | Redis + Caffeine |
| 认证 | Sa-Token |

## 快速开始

### 环境要求

- JDK 17+
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
