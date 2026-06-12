# PRD — DX4 外置 Goal 配置与 Goal 工坊（配置即能力 / 测试环境对话式构建 / 指纹化导出导入）

> 交付对象：实现方（Codex）
> 前置版本：0.2.0-SNAPSHOT main（441 tests，DX3 已落自动 Seeding 与类型级转换器；**DX3 提交推送为本期开工前提**）
> 动机：GOAP 架构里"流程"从来不是硬编码——链由规划器搜索，代码里只剩 Goal 声明。把 Goal 声明外置（A），新业务组合从发版任务降为配置变更；再把声明的"起草"交给 LLM 在测试环境对话完成（B），上线退化为一次带指纹校验的配置导入。LLM 起草的是声明、不是计划——"LLM 不生成 Plan"铁律在设计期同样成立。
> 状态：可直接实施，A/B 分阶段验收（B 依赖 A）

---

## 0. 一句话目标

测试环境里一段对话产出一个新业务能力（Goal 定义），人确认后导出带指纹的 bundle；生产导入即用——每一步运行时闸门（风险/人审/限额/补偿）原封不动。

## 0.1 兼容性硬约束

- 现有 441 测试全绿；全部新 API `@Experimental(since="0.2.0")`；japicmp 通过；
- `actiongraph.studio.enabled` 默认 **false**；不开启时无任何行为变化、不暴露任何端点；
- 风险等级/人审标记/补偿**仍不可外置**（F0 以来的裁决，工坊不开例外）；
- claims 域冻结不动；不新建模块（工坊落 console，治理台账记录其对 llm 模块的新增依赖）。

---

## 1. 范围

### 1.1 In scope
- **DX4-A 外置 Goal 配置**：yml 定义 Goal（四源合并进 GoalCatalog）+ 启动校验 + 参数类型化绑定 + Goal 定义指纹进 trace；
- **DX4-B Goal 工坊**（测试环境专用）：`@ActionGraphAction.description` 前置 + 起草会话（清单 prompt → 草案 → 校验自动回喂修正）+ 风险画像确认 + bundle 导出 + 生产侧指纹校验导入；
- console 最小工坊页面（API 优先，页面从简）；
- 文档：learning-path 增"配置即能力"章（Packaging 层名分）+ 工坊操作手册 + bundle 变更管理指引。

### 1.2 Out of scope
- 运行时动态注册/热生效（DB 后端、免重启——MS3 控制面平台化议题）；
- bundle 数字签名/HMAC（开放问题 #1，v1 用指纹+变更管理流程兜底）；
- 工坊多人协作/草稿库管理（v1 单会话内存态）；
- 外置 Action（永不）；外置校验规则扩展。

---

## 2. DX4-A 外置 Goal 配置

### 2.1 配置形态
```yaml
actiongraph:
  goals:
    definitions:
      - type: product.restock-and-notify
        description: 为指定商品补货并通知负责人
        enabled: true                      # false = 不注册（环境差异用 Spring profile 文件天然支持）
        target-conditions: [product:RESTOCKED, notify:OWNER_NOTIFIED]
        seed-conditions: [product:ID_PRESENT]
        parameters:
          - name: productId
            type: com.example.product.ProductRef    # FQCN 或内置别名 string/int/long/decimal/boolean
            required: true
            description: 商品编号
            example: P-100
```

### 2.2 裁决 A1：四源合并，重复即死
GoalCatalog 聚合顺序：Bean 直供 → Contribution → 注解 → **配置**。任意两源重复 type → 启动失败并报出**两个来源**（含配置项的文件/索引定位）。沿用既有冲突纪律，不引入覆盖语义——配置不是 patch 机制，是第四个对等来源。

### 2.3 裁决 A2：参数类型在启动期闭环，绝不留到运行时
配置参数的 `type` 解析：内置别名 → 内置白名单；FQCN → 类加载 + `TypedGoalValueConverter` 注册表（DX3-B）。**类不存在 / 无对应转换器 → 启动期 `ActionGraphConfigurationException`**。绑定与上黑板复用 DX3 的 `GoalParameterBinder` 与自动 Seeding——配置 Goal 天然无 Java schema 类，自动 Seeding 按参数逐个绑定后以参数名为 key 上黑板（与 schema record 场景的差异写进文档）。

### 2.4 裁决 A3：配置 Goal 与代码 Goal 同权同验
配置源注册完成后才执行 DX1 启动校验——不可达的配置 Goal 同样让应用起不来，诊断同格式。`enabled:false` 的不注册、不校验。

### 2.5 裁决 A4：指纹双轨进审计
- **Action 图指纹**：`sha256(sorted(actionId|preconditions|effects|riskLevel|requiresHumanReview))`，启动时算一次，记入启动日志；
- **Goal 定义指纹**：每个 Goal 声明的规范化哈希；
- 每次运行的 `RUN_STARTED` trace data 增加 `goalFingerprint` 与 `actionGraphFingerprint`——审计可回答"这次运行依据哪版流程定义、哪版能力图谱"。

---

## 3. DX4-B Goal 工坊

### 3.0 前置：`@ActionGraphAction` + `description()`（additive）
注解加 `String description() default ""`；`Action` 接口加 `default String description() { return ""; }`；注解工厂打通。同步进入 GoalCatalogPromptRenderer 的清单与 `ActionGraphExporter` 节点标注。**没有 description 的 Action 在工坊清单里以 id 裸奔——文档要求接入方为可组合的 Action 补描述**（不强制，工坊对缺描述者在风险画像中标注"无描述"）。

### 3.1 裁决 B1：工坊只在非生产存在，硬挡
- `actiongraph.studio.enabled = false`（默认）；
- `actiongraph.studio.forbidden-profiles = prod,production`（可配）：active profile 命中且 enabled=true → **启动失败**（不是告警）；
- 生产侧零工坊端点——导入就是把 bundle 文件放进配置目录，走 2.x 的启动加载，无任何新攻击面。

### 3.2 起草会话（console 模块，token 鉴权沿用 console 既有机制）
```
POST /actiongraph/studio/sessions          {description: "想要…的能力"}
POST /actiongraph/studio/sessions/{id}/refine    {feedback: "..."}
POST /actiongraph/studio/sessions/{id}/approve   {approver: "..."}
GET  /actiongraph/studio/sessions/{id}           → 当前草案+校验+预演+风险画像
```
会话状态 v1 为内存态（测试环境可接受，重启即弃草稿）。

### 3.3 裁决 B2：校验回喂的自动修正循环由框架驱动
每轮：LLM 产出 Goal 草案（JSON，严格解析，沿用 StructuredOutputParser 容错模式）→ 框架立即跑 DX1 校验 → 不可达则把**诊断文本原样回喂** LLM 重起草，自动循环至多 `actiongraph.studio.max-auto-repairs`（默认 3）轮；仍不可达才把诊断呈现给用户。DX1 的"缺哪个条件、最接近的拼写"诊断即 LLM 修正指令——零额外 prompt 工程。

### 3.4 裁决 B3：LLM 的输入清单与输出契约
- 清单 prompt = Action（id/description/preconditions/effects/riskLevel/requiresHumanReview）+ 全部已知 Condition + 既有 goalType 列表（避免撞名/重复造）；来源即 registry/catalog，与图导出同源；
- LLM 输出仅限 Goal 声明字段（type/description/target/seed/parameters）——**输出里出现步骤序列/Action 顺序的内容直接丢弃**（解析器不设这些字段，结构上不可能泄入）；
- 风险画像由框架计算（预演计划 → 各步风险/人审/限额标注），**不让 LLM 自评风险**。

### 3.5 裁决 B4：approve 才落盘，bundle 自带验证上下文
`approve` 后写出 bundle（单 yml 文件，DX4-A 格式 + metadata 块）：
```yaml
actiongraph-bundle:
  bundle-fingerprint: <sha256 of definitions>
  action-graph-fingerprint: <验证时所依据的图谱指纹>
  validated-at: 2026-06-12T...
  approved-by: zhang.san
  source-env: test
  definitions: [ ...DX4-A 格式... ]
```

### 3.6 裁决 B5：导入即重验，指纹漂移默认拒绝
生产加载 bundle 时：
1. `bundle-fingerprint` 自校验（内容未被篡改）；
2. 比对 `action-graph-fingerprint` 与本环境实算值——不一致按 `actiongraph.goals.bundle.fingerprint-mismatch = FAIL | WARN`（默认 **FAIL**：测试验证过 ≠ 生产可达，版本漂移必须显式确认）；
3. **无论指纹是否一致，DX1 可达性校验照跑**（指纹一致只说明图谱相同，校验是最后防线）。

---

## 4. 验收标准（Definition of Done）

### A 段
1. 纯配置定义 Goal → `actionGraph.start(type, params)` 端到端 COMPLETED（参数经类型转换器上黑板）；
2. 配置与注解重复 type → 启动失败报两来源（含配置文件定位）；参数 type 不可解析 → 启动失败；
3. 不可达的配置 Goal → 启动失败，诊断同 DX1 格式；`enabled:false` 不注册；
4. `RUN_STARTED` 含 `goalFingerprint`/`actionGraphFingerprint`；同定义重启指纹稳定，定义变更指纹变化。

### B 段
5. `description` additive 全链路（注解→Action 接口→prompt 清单→图导出标注），既有测试零语义变化；
6. 工坊 happy path（假 LlmClient）：描述 → 草案 → 校验通过 → 返回预演+风险画像（含 HIGH/人审标注）→ approve → bundle 落盘含全部 metadata；
7. **自动修正循环**：首轮草案故意不可达 → 框架回喂诊断 → 次轮修正通过（断言 LLM 收到的第二轮 prompt 含诊断文本）；超过 max-auto-repairs 仍不可达 → 诊断呈现给调用方而非死循环；
8. 生产侧导入：图谱一致 → 加载成功且重验照跑；**漂移**（改一个 Action 的 precondition）→ 默认启动失败、WARN 模式放行但日志告警，两种都重验可达性；bundle 内容被篡改 → 自校验失败拒载；
9. 禁产硬挡：`prod` profile + studio enabled → 启动失败；默认配置下工坊端点不存在（404/未注册断言）；
10. 回归与规范：441 既有测试全绿；japicmp、文档守卫、`./gradlew build --rerun-tasks` 全绿；**本期交付必须已提交推送，提供 commit 哈希**（DX3 的教训写进 DoD）。

## 5. 实施顺序

A2/A1（配置源+绑定）→ A3（校验接入）→ A4（指纹）→ **A 段验收** → B0（description）→ B2/B3（会话与修正循环）→ B4/B5（bundle 导出导入）→ B1（禁产硬挡贯穿实现）→ B 段验收。

## 6. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | bundle 签名（HMAC/证书） | v1 不做，指纹+变更管理兜底；金融试点要求出现即升级 |
| 2 | 工坊草稿持久化/多人协作 | v1 内存态单会话 |
| 3 | 配置 Goal 的参数上黑板 key | 参数名字符串 key；需要类型 key 的写 schema record（代码路径） |
| 4 | 运行时热加载 bundle | 不做，重启生效是金融合规偏好；MS3 再议 |
| 5 | 工坊页面形态 | API 完整 + 单页极简表单；富交互留给 console 演进 |
