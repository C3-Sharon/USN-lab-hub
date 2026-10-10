# USN-lab-hub

实验室考勤与人员管理系统。项目采用前后端分离结构，并在现有成员、登录、考勤能力基础上继续扩展 IoT 硬件项目管理能力。

- `backend/`: Spring Boot 3.1.5 + MyBatis-Plus 3.5.5 + MySQL 8 + Redis + Flyway
- `frontend/`: Vue 3 + Vite + Element Plus + Vue Router + Axios
- `docs/agent-guides/`: 团队协作、Agent 任务、API/MQTT 契约和验收规则

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

## 团队协作与分支管理

暑假远程阶段以 GitHub 作为唯一主协作仓库。实验室 GitLab 因为需要实验室 WiFi，返校或能连接实验室网络后再从 GitHub 同步归档。

推荐分支：

| 分支 | 用途 | 规则 |
| --- | --- | --- |
| `main` | 稳定演示分支 | 只接收从 `dev` 合入的可演示版本 |
| `dev` | 日常集成分支 | 三人功能分支通过 PR 合入这里 |
| `feature/iot-product` | 产品需求、字段字典、项目文档 | 产品负责人维护 |
| `feature/iot-frontend` | 前端 IoT 页面、路由、mock/API 对接 | 前端负责人维护 |
| `feature/iot-backend` | 后端 IoT 模块、数据库、API、MQTT 接入 | 后端负责人维护 |
| `feature/iot-mqtt-demo` | MQTT 模拟器、硬件联调脚本 | 可选联调分支 |

初次克隆后切分支：

```bash
git clone git@github.com:C3-Sharon/USN-lab-hub.git
cd USN-lab-hub
git checkout dev
git pull origin dev
```

个人开发分支示例：

```bash
# 产品
git checkout dev
git pull origin dev
git checkout -b feature/iot-product

# 前端
git checkout dev
git pull origin dev
git checkout -b feature/iot-frontend

# 后端
git checkout dev
git pull origin dev
git checkout -b feature/iot-backend
```

合并流程：

```text
feature/iot-product  -> Pull Request -> dev
feature/iot-frontend -> Pull Request -> dev
feature/iot-backend  -> Pull Request -> dev

阶段验收后：
dev -> Pull Request -> main
```

协作规则：

- 不直接推 `main`。
- 平时开发都从 `dev` 切分支。
- 合并到 `dev` 必须走 Pull Request。
- `main` 只保留能给老师演示的稳定版本。
- 每个 PR 必须写清楚测试方法和预期结果。
- 合并前至少让另一个成员看一眼。
- 不通过 QQ/微信压缩包合代码。
- 不提交真实服务器密码、数据库密码、私钥、Token。

建议在 GitHub 仓库设置中保护 `main`：

- Require a pull request before merging
- Require approvals: 1
- Block force pushes
- Restrict deletions

`dev` 初期可以宽松一些，避免新手阶段被流程卡住；团队熟悉后再考虑增加保护规则。

## IoT 扩展约定

新增 IoT 能力必须优先阅读并遵守 `docs/agent-guides`：

- `00_SHARED_AGENTS.md`
- 角色对应指南：产品、前端或后端
- `04_API_CONTRACT.md`
- 涉及硬件或 MQTT 时阅读 `05_MQTT_CONTRACT.md`
- `06_VERTICAL_SLICE_PLAN.md`
- `07_TESTING_AND_ACCEPTANCE.md`
- `08_AGENT_TASK_TEMPLATE.md`

命名约定：

- 新增 IoT 后端接口统一使用 `/api/iot/**`。
- IoT 数据表使用 `iot_` 前缀。
- 实验室项目表使用 `lab_project`。
- 前端 IoT 页面放在 `frontend/src/views/iot`。
- 前端 IoT API 封装放在 `frontend/src/api/iot.js`。
- 不随意改动已有成员、登录、考勤接口。

MVP 阶段只围绕最小纵向切片推进：

```text
People -> Project -> Device -> Telemetry -> Alert -> Recommendation -> Command -> ACK -> Operation Log
人员 -> 项目 -> 设备 -> 数据 -> 告警 -> 建议 -> 控制指令 -> 硬件回执 -> 操作日志
```

## GitHub 会前准备

第一次线上会前建议完成：

- 邀请两位同学加入 GitHub 仓库，权限给 `Write`。
- 确认三个人都能 clone 仓库。
- 确认 `dev` 分支存在并可拉取。
- 告诉大家先阅读 `docs/planning` 和 `docs/agent-guides`。
- 建立 GitHub Issues 或 Project。
- 准备第一次会议时让大家复制 `09_STARTER_PROMPTS.md` 中对应角色的 prompt。

建议初始 Issues：

- `[Product] 确认首批硬件接入清单`
- `[Product] 输出 PM-001 最小纵向切片需求`
- `[Frontend] 读取前端结构并输出 IoT 页面落点`
- `[Frontend] 设计 IoT 路由和 mock 数据结构`
- `[Backend] 读取后端结构并输出 IoT 模块落点`
- `[Backend] 输出 IoT 数据库表设计草案`
- `[Backend] 输出 MQTT 接入和模拟器方案`
- `[Team] 确认 API_CONTRACT 和 MQTT_CONTRACT 第一版`

建议标签：

```text
product
frontend
backend
mqtt
contract
testing
blocked
week-1
```

## GitHub 与实验室 GitLab

暑假远程开发：

```text
GitHub 是主仓库。
GitLab 暂不参与日常开发。
```

返校或连接实验室 WiFi 后，可把 GitHub 的 `main` 和 `dev` 同步到实验室 GitLab：

```bash
git remote add gitlab <实验室GitLab仓库地址>
git push gitlab main
git push gitlab dev
```

注意：GitHub 和 GitLab 不要同时接受独立代码变更，避免双主仓库导致历史分叉。

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

## 暑假远程协作分支流程

暑假远程阶段以 GitHub 作为主协作仓库。实验室 GitLab 由于只能在连接实验室 WiFi 时访问，暂时作为后续同步归档仓库，不和 GitHub 双主线并行开发。

分支职责：

| 分支 | 用途 | 维护规则 |
| --- | --- | --- |
| `main` | 稳定演示版 | 只放可以给老师展示的版本，不直接开发 |
| `dev` | 日常集成版 | 三个人的功能先合到这里联调 |
| `feature/iot-product` | 产品与文档 | 产品经理维护需求、字段、会议纪要、页面说明 |
| `feature/iot-frontend` | 前端实现 | 前端负责人维护 Vue 页面、mock、接口联调 |
| `feature/iot-backend` | 后端实现 | 后端负责人维护数据库、接口、MQTT、告警、日志 |
| `feature/iot-mqtt-demo` | MQTT 联调 | 后端或硬件组维护模拟器和联调脚本 |

推荐流向：

```text
feature/*  ->  dev  ->  main
个人开发      团队联调   稳定展示
```

首次拉取项目后，先切到 `dev`，再创建自己的功能分支：

```bash
git clone git@github.com:C3-Sharon/USN-lab-hub.git
cd USN-lab-hub

git checkout dev
git pull origin dev

# 产品经理
git checkout -b feature/iot-product

# 前端负责人
git checkout -b feature/iot-frontend

# 后端负责人
git checkout -b feature/iot-backend
```

日常开始工作前：

```bash
git checkout feature/iot-backend   # 替换为自己的分支
git fetch origin
git merge origin/dev
git status --short
```

日常完成一个小任务后：

```bash
git status --short
git add .
git commit -m "feat: add iot device api draft"
git push -u origin feature/iot-backend
```

然后在 GitHub 创建 Pull Request：

```text
base: dev
compare: feature/iot-backend
```

不要把日常功能 PR 到 `main`。每周阶段验收通过后，再从 `dev` 创建 PR 到 `main`：

```text
base: main
compare: dev
title: release: week 1 integration
```

PR 描述必须包含：

```text
改了什么：
- 

如何测试：
- 

影响契约：
- API_CONTRACT：是/否
- MQTT_CONTRACT：是/否
- 数据库表：是/否
- 前端字段：是/否

需要谁配合：
- 
```

协作规则：

- 不直接向 `main` 推送代码。
- 不用 QQ/微信压缩包合并代码。
- 每个 PR 至少让另一位同学看一眼。
- 字段或接口变化必须同步 `docs/agent-guides/04_API_CONTRACT.md`。
- MQTT Topic 或 payload 变化必须同步 `docs/agent-guides/05_MQTT_CONTRACT.md`。
- 每个 agent 任务必须给出运行方法、测试方法、预期结果和契约影响。

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
3. 后端默认 dev 配置连接 localhost:3306/usn_hub，用户名默认 root、密码默认空；如果本机不同，请通过环境变量覆盖，不要提交个人密码。

运行验证：
1. cd backend && mvnw.cmd -DskipTests package
2. cd backend && mvnw.cmd spring-boot:run
3. cd frontend && npm install && npm run dev
4. 打开 http://localhost:5173
5. 使用 admin/admin123 登录管理员账号，使用 20260001/20260001 登录学生账号。

首次创建协作分支：（请先咨询我是负责哪个部分的负责人）
1. git checkout dev
2. git pull origin dev
3. 根据角色创建分支：
   - 产品经理：git checkout -b feature/iot-product
   - 前端负责人：git checkout -b feature/iot-frontend
   - 后端负责人：git checkout -b feature/iot-backend
4. 后续所有日常开发都在自己的 feature 分支完成，通过 Pull Request 合并到 dev。
5. 不要直接向 main 推送代码；main 只接收每周验收后的稳定版本。

约束：
- 不要提交 node_modules、dist、target、.idea、.deploy。
- 不要提交任何真实服务器密码、数据库密码、私钥或 Token。
- 修改前先检查 git status，避免覆盖其他人的未提交改动。
- 新增 IoT 代码前必须先阅读 docs/agent-guides。
- 新增 IoT 接口使用 /api/iot/**，数据表使用 iot_ 前缀。
- 每次 PR 必须包含测试方法和预期结果。
- 如果改动涉及 API 字段、数据库表、MQTT 消息或前端字段，必须同步更新对应契约文档。
```
