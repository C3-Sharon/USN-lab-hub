# USN-lab-hub

实验室考勤与人员管理系统。项目采用前后端分离结构：

- `backend/`: Spring Boot 3.1.5 + MyBatis-Plus 3.5.5 + MySQL 8 + Redis + Flyway
- `frontend/`: Vue 3 + Vite + Element Plus + Vue Router + Axios

## 默认初始化账号

Flyway 初始化脚本会插入以下演示账号，便于本地启动后直接验证系统流程。

| 角色 | 账号 | 初始密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `admin123` | 可访问成员管理和考勤检查页面 |
| 学生 | `20260001` | `20260001` | 可访问个人考勤控制台 |

生产环境部署后请立即修改这些默认密码，或在上线前调整 `backend/src/main/resources/db/migration/V1__Init_Tables.sql` 中的初始化数据。

## 环境要求

| 组件 | 推荐版本 |
| --- | --- |
| JDK | 17 或 21 |
| Maven | 可使用项目自带 `backend/mvnw` / `backend/mvnw.cmd` |
| Node.js | 20 LTS |
| MySQL | 8.x |
| Redis | 6.x 或更高 |

## 本地数据库准备

默认 `dev` 配置读取：

- MySQL 地址：`localhost:3306`
- 数据库名：`usn_hub`
- 用户名：默认 `root`，可通过 `DB_USERNAME` 覆盖
- 密码：默认空，可通过 `DB_PASSWORD` 覆盖
- Redis 地址：`127.0.0.1:6379`

创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS usn_hub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果你的 MySQL 需要密码，请在本机设置环境变量 `DB_PASSWORD`，不要把个人密码提交到仓库。

首次启动后，Flyway 会自动执行 `backend/src/main/resources/db/migration/V1__Init_Tables.sql`，创建表并插入默认账号。

如果本地库已经执行过旧版迁移脚本，建议开发阶段直接重建空库：

```sql
DROP DATABASE IF EXISTS usn_hub;
CREATE DATABASE usn_hub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 本地启动

后端：

```bash
cd backend

# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

后端默认地址：

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`

前端：

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：

- `http://localhost:5173`

Vite 已配置代理：

- `/usnhub` -> `http://localhost:8080`
- `/admin` -> `http://localhost:8080`

因此本地开发默认不需要配置 `VITE_API_BASE_URL`。如需自定义，可参考 `frontend/.env.example`。

## 本地构建验证

后端打包：

```bash
cd backend

# Windows
mvnw.cmd -DskipTests package

# macOS / Linux
./mvnw -DskipTests package
```

前端打包：

```bash
cd frontend
npm run build
```

构建产物：

- 后端 jar: `backend/target/lab-hub-0.0.1-SNAPSHOT.jar`
- 前端静态文件: `frontend/dist/`

这些构建产物已在 `.gitignore` 中排除，不应提交到 GitHub。

## 生产部署参考

推荐部署形态：

- Nginx 对外提供 80/443 访问
- Nginx 托管前端静态文件
- Nginx 将 `/usnhub/` 和 `/admin/` 反向代理到后端
- 后端 Spring Boot 以 systemd 服务运行，仅监听 `127.0.0.1:8081`
- MySQL 和 Redis 不直接暴露公网

后端生产启动示例：

```bash
java -jar backend/target/lab-hub-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --server.address=127.0.0.1 \
  --server.port=8081
```

生产环境变量示例：

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/usn_hub?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
export DB_USERNAME='usn'
export DB_PASSWORD='<your-db-password>'
export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
export REDIS_DATABASE='0'
```

Nginx 反向代理示例：

```nginx
server {
    listen 80;
    server_name _;

    root /var/www/usn-lab-hub;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /usnhub/ {
        proxy_pass http://127.0.0.1:8081/usnhub/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /admin/ {
        proxy_pass http://127.0.0.1:8081/admin/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

如果只允许局域网访问，可以在 Nginx 的 `server` 或 `location` 中增加网段限制，例如：

```nginx
allow 10.16.0.0/16;
deny all;
```

## Git 提交注意事项

不要提交以下内容：

- `frontend/node_modules/`
- `frontend/dist/`
- `backend/target/`
- `.deploy/`
- `.idea/`
- 真实服务器密码、数据库密码、私钥、Token

提交前建议执行：

```bash
git status --short
cd backend && mvnw.cmd -DskipTests package
cd ../frontend && npm run build
```

macOS / Linux 将 `mvnw.cmd` 替换为 `./mvnw`。

## 给 Agent 的协作 Prompt

可以把下面这段发给代码 Agent，让它拉取项目并完成本地运行检查：

```text
你是这个项目的协作开发 Agent。请拉取 GitHub 仓库 C3-Sharon/USN-lab-hub，并在本地完成可运行性检查。

技术栈：
- backend: Spring Boot 3.1.5, MyBatis-Plus 3.5.5, MySQL 8, Redis, Flyway
- frontend: Vue 3, Vite, Element Plus, Vue Router, Axios

本地准备：
1. 确认 JDK 17+、Node.js 20+、MySQL 8、Redis 可用。
2. 创建 MySQL 数据库：
   CREATE DATABASE IF NOT EXISTS usn_hub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
3. 后端默认 dev 配置连接 `localhost:3306/usn_hub`，用户名默认 `root`、密码默认空；如果本机不同，请通过环境变量覆盖，不要提交个人密码。

运行验证：
1. cd backend && mvnw.cmd -DskipTests package
2. cd backend && mvnw.cmd spring-boot:run
3. cd frontend && npm install && npm run dev
4. 打开 http://localhost:5173
5. 使用 admin/admin123 登录管理员账号，使用 20260001/20260001 登录学生账号。

约束：
- 不要提交 node_modules、dist、target、.idea、.deploy。
- 不要提交任何真实服务器密码、数据库密码、私钥或 Token。
- 修改前先检查 git status，避免覆盖其他人的未提交改动。
```
