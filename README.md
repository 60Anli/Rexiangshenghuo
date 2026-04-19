# Rexiangshenghuo

`Rexiangshenghuo` 是一个基于 Spring Boot 的本地生活服务示例项目，当前仓库包含后端服务、前端静态页面以及一份 nginx 代理配置示例。

## 项目结构

```text
.
├─ src/                  后端 Spring Boot 源码
├─ frontend/hmdp/        前端静态页面、样式、脚本和图片资源
├─ deploy/nginx/         nginx 配置示例
├─ pom.xml               Maven 依赖配置
└─ README.md
```

## 技术栈

- Java 8
- Spring Boot 2.3
- MyBatis-Plus
- MySQL
- Redis
- Nginx
- Vue + Axios + Element UI（静态前端页面）

## 环境要求

- JDK 8
- Maven 3.6+
- MySQL 5.7/8.x
- Redis 6.x+
- Nginx 1.18+

## 后端启动

1. 创建数据库并导入初始化脚本：

```sql
source src/main/resources/db/hmdp.sql;
```

2. 按本地环境配置数据库和 Redis。

项目默认读取：

- `src/main/resources/application.yaml`
- `src/main/resources/application-local.yaml`

其中 `application.yaml` 使用环境变量占位，推荐把本地敏感配置放在未提交的 `application-local.yaml` 中。

3. 启动 Spring Boot 服务：

```bash
mvn spring-boot:run
```

默认后端端口：

```text
8081
```

## 前端启动

前端页面位于：

```text
frontend/hmdp
```

这是静态页面工程，通常通过 nginx 托管访问。

## Nginx 配置

仓库中提供了一份示例配置：

```text
deploy/nginx/nginx.conf
```

配置要点：

- nginx 对外端口为 `8080`
- `/` 指向前端静态目录
- `/api` 转发到后端服务

如果你本地使用单实例后端，可以将 `/api` 代理到：

```text
http://127.0.0.1:8081
```

## 开发说明

- 后端代码位于 `src/main/java/com/hmdp`
- Mapper XML 位于 `src/main/resources/mapper`
- Lua 脚本位于 `src/main/resources`
- 前端资源位于 `frontend/hmdp`

## Git 常用流程

```bash
git status
git add .
git commit -m "feat: update project"
git push
```

## 注意事项

- 不要把数据库、Redis 等敏感账号密码直接提交到仓库
- `target/`、`.idea/`、`backup/` 等本地文件已被忽略，不建议提交
- 如果需要部署到其他机器，请根据实际环境修改数据库、Redis 和 nginx 配置
