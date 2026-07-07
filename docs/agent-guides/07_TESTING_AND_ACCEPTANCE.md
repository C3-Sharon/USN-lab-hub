# Testing and Acceptance Guide / 测试与验收指南

> 中文说明：本文件用于降低 vibecoding 水平参差造成的质量风险。任何 agent 编码任务都必须输出测试方法、预期结果和契约影响。

## 1. Non-negotiable Rule / 硬规则

Do not finish a coding task without a verification section.

任何编码任务都不能只交代码，必须同时交：

- 改动摘要
- 涉及文件
- 如何运行
- 如何测试
- 测试数据
- 预期结果
- 契约影响
- 需要谁配合
- 已知风险

## 2. Required Completion Format / 必须输出格式

Every agent task should end with:

```text
本次改动：
- 

涉及文件：
- 

如何运行：
- 

如何测试：
1. 
2. 
3. 

测试数据：
- 

预期结果：
- 

影响的契约：
- API_CONTRACT：是/否
- MQTT_CONTRACT：是/否
- 前端字段：是/否
- 数据库表：是/否

需要谁配合：
- 产品：是/否，原因：
- 前端：是/否，原因：
- 后端：是/否，原因：
- 硬件组：是/否，原因：

已知风险：
- 
```

## 3. Frontend Test Requirements / 前端测试要求

Every frontend task must provide:

- Page route / 页面路径
- Mock data location / mock 数据位置
- APIs used / 调用接口
- Normal data test / 正常数据测试
- Empty data test / 空数据测试
- Error state test / 错误状态测试
- Loading state test / 加载状态测试
- Button/action test / 按钮操作测试

Example:

```text
前端验证方法：
1. cd frontend
2. npm run dev
3. 登录管理员账号
4. 打开 /iot/devices/1
5. 确认 PM-001 最新数据卡片显示 voltage/current/power
6. 切换为空数组 mock，确认空状态显示
7. 模拟接口 500，确认错误提示显示
8. 点击 SET_SAMPLE_INTERVAL，确认按钮 loading 和指令状态更新
```

## 4. Backend Test Requirements / 后端测试要求

Every backend task must provide:

- Startup command / 启动命令
- Database preparation / 数据库准备
- API request example / API 请求示例
- Expected JSON response / 预期 JSON
- Exception tests / 异常测试
- Contract check / 契约检查

Minimum exception tests:

- Missing required params / 参数缺失
- Not found / 设备或项目不存在
- Duplicate code / 编号重复
- Unauthorized / 未登录或无权限
- Invalid status / 非法状态

## 5. API Contract Test / API 契约测试

For every API:

```text
1. Check path and method match 04_API_CONTRACT.md.
2. Check request params match contract.
3. Check response fields match contract.
4. Check frontend page knows how to use the response.
5. If mismatch exists, update contract first.
```

中文：

接口测试不只是“能返回 200”，还要检查字段是否和契约一致，前端是否能按契约使用。

## 6. MQTT Test Requirements / MQTT 测试要求

For every MQTT task, provide:

- Broker address
- Topic
- Payload
- Publish method
- Backend expected log
- Database expected result
- Frontend expected result

Telemetry expected result:

```text
1. Backend logs telemetry received.
2. iot_telemetry_raw inserts one row.
3. iot_metric_data inserts one row per metric.
4. Redis latest value updates if enabled.
5. Frontend shows latest metric values.
```

Command expected result:

```text
1. Frontend sends command API.
2. Backend creates command_record.
3. Backend publishes MQTT command.
4. Hardware/simulator receives command.
5. Hardware/simulator publishes command_ack.
6. Backend updates command status to ACKED.
7. Operation log records the action.
```

## 7. Vertical Slice Acceptance / 最小纵向切片验收

The MVP slice is accepted only when these pass:

1. Create or load project `power-monitor`.
2. Create or load device `PM-001`.
3. Publish MQTT telemetry.
4. Store raw payload.
5. Store parsed metrics.
6. Query latest metrics.
7. Show latest metrics on frontend.
8. Show power history chart.
9. Trigger `power > 100` alert.
10. Show alert and recommendation.
11. Send `SET_SAMPLE_INTERVAL` command.
12. Receive ACK.
13. Show command status `ACKED`.
14. Show operation log.

## 8. Weekly Acceptance / 每周验收

Every Friday or last workday:

Product must provide:

- Updated requirement or field dictionary
- Page acceptance result
- Demo script update
- Risk list

Frontend must provide:

- Page route
- Screenshot
- APIs used
- Test method
- Unintegrated blockers

Backend must provide:

- API list
- Test command
- DB changes
- MQTT test result
- Expected JSON

Hardware group should provide:

- Device code
- Topic
- Payload
- Publish log or screenshot
- ACK log or screenshot

## 9. Vibecoding Safety Rules / Vibecoding 防翻车规则

- Make small tasks. Do not ask an agent to implement a huge module at once.
- Always ask the agent to read contract files first.
- Never accept code without test steps.
- Do not use vague prompts such as "optimize this".
- Do not let the agent invent fields.
- Mock first, integrate later, but mock must match the API contract.
- Build the vertical slice before expanding features.
- Update contract before changing code when fields change.

中文：

- 小步提交，小步测试。
- 先读契约，再写代码。
- 没有测试步骤的 agent 输出不算完成。
- 不用“优化一下”这种模糊 prompt。
- 字段必须来自契约。
- 先 mock 后联调，但 mock 必须和契约一致。
- 先最小闭环，后扩展功能。

