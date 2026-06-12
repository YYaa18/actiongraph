# ActionGraph Golden Path

**Layer: Golden Path**

本文定义 ActionGraph 的标准首次接入路径。它不移除任何既有 SPI，只是把“应用代码优先使用什么、什么时候进入进阶层”说清楚，避免新团队第一天就被底层接口淹没。

## 标准入口

应用代码优先使用根门面：

```java
RunResult result = actionGraph.start("order.cancel", Map.of("orderId", "O100"));

ChatResult chat = actionGraph.chat("帮客户 C001 准备续约报价");

RunResult resumed = actionGraph.resume(runId);
```

`ActionGraph` 有意保持很窄：启动运行、从自然语言进入运行、恢复运行。它不负责审批决策写入、外部事件投递、崩溃恢复扫描、Console 导出或图预览渲染。这些属于运营面，由审批系统、MQ listener、调度器和审计工具使用。

## 四层模型

| Layer | Surface | When to use |
|---|---|---|
| Golden Path | `@ActionGraphAction`, `@ActionGraphGoal(schema=...)`, Spring starter 扫描, 根门面 `ActionGraph` | 首次接入与绝大多数应用代码 |
| Packaging | `ActionGraphContribution` | 把一个业务域发布为可复用模块或库 |
| SPI | `Action`, `GoalBlackboardSeeder`, 手工 `GoalDefinition`, registries, validator, exporter | 框架扩展和高级集成代码 |
| Internal | `@Internal` API 与实现类 | 不面向应用代码 |

## Seeding 边界

Golden Path 中，goal schema record 就是 seeding 契约。如果存在 `@ActionGraphGoal(schema = MyGoal.class)`，且该 goal type 没有显式 Seeder，ActionGraph 会自动把请求参数绑定为 schema record，把该 record 写入 Blackboard，并添加 Goal 声明的 seed conditions。

ActionGraph 到此停止。把 schema record 映射为业务实体属于业务语义，应放在 Action 或应用服务中。框架不会做反射式 bean copy。

显式 Seeder 仍然保留，用于多对象写入、复杂引用解析、额外 seed conditions 或对接遗留装配逻辑。注册优先级是显式 Seeder 高于自动 schema seeding。

## 错误哲学

`start(goalType, parameters)` 是代码发起入口。未知 goal type、缺少必填参数属于调用方错误，因此抛出 `ActionGraphInputException`。

`chat(input, knownParameters)` 是用户发起入口。缺少参数属于正常对话分支，因此返回 `ChatResult.started() == false` 和澄清问题。

## Runtime API 定位

`ActionGraphRuntimeApiService` 是同一根门面的控制面 HTTP 适配。应用代码应优先注入 `ActionGraph`；HTTP controller、Java 8 网关和跨进程适配器可以继续使用 Runtime API 的 DTO 形态。
