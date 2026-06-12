# PRD — DX3 Schema 即 Seeder（声明式自动 Seeding / 类型级转换器 / 绑定校验）

> 交付对象：实现方（Codex）
> 前置版本：0.2.0-SNAPSHOT main（423 tests，DX2 已落正门与金路径）
> 动机：真实接入应用（springboot-product-mvc）的 Seeder 类 79 行**零业务逻辑**——全部是"字符串参数→类型化对象→上黑板"的机械搬运。机械过程归框架：让 Seeder 在常规场景下**消失**，从默认概念降级为自定义装配的逃生口。金路径概念预算随之从三注解降为两注解。
> 状态：可直接实施

---

## 0. 一句话目标

`@ActionGraphGoal` 声明了 schema，框架就自动完成 Seeding（绑定 → 上黑板 → 点亮种子条件）；接入方常规场景**一行 Seeder 代码都不写**。

## 0.1 兼容性硬约束

- 现有 423 测试全绿；所有现有 Seeder（注解/接口/Contribution）行为零变化；
- 新公共 API `@Experimental(since="0.2.0")`；japicmp 通过；claims 域冻结不动。

---

## 1. 范围

### 1.1 In scope
- DX3-A 自动 Seeding：schema 驱动的框架生成 Seeder（核心）；
- DX3-B `TypedGoalValueConverter<T>`：按目标类型注册的转换器（地基）；
- DX3-C 绑定管道抽取：`GoalParameterBinder`（自动 Seeding 与注解 Seeder 共用）；
- DX3-D `@GoalSchema` 绑定级校验（首期仅 `atLeastOne`）；
- DX3-E 无参 Goal 的自动"仅条件"Seeder（消灭空方法仪式）；
- dogfood：samples/renewal 删除显式 Seeder 改走自动 Seeding；
- 文档：golden-path / learning-path / quick-start 更新——Seeder 移出 L0 词汇表；L0 守卫断言同步调整。

### 1.2 Out of scope
- 反射式 bean-copy / 实体自动映射（见裁决 E，永不做）；
- 嵌套 schema（record 套 record）与集合组件绑定（试点反馈后再议）；
- 校验规则扩展（min/max/pattern 等——首期只做 atLeastOne，别造 Bean Validation 的轮子，真有需求接 JSR-380 评估进开放问题）；
- springboot-product-mvc 的改造（仓库外，验收后作为真实验证场另行执行）。

---

## 2. 核心裁决

### 裁决 A：Schema 即 Seeder——自动生成，显式优先
- `@ActionGraphGoal(schema = X.class)` 存在且该 goalType **无显式 Seeder** 时，框架注册自动 Seeder：
  ```
  解释参数 → 按 X 的规范构造器绑定（管道见裁决 C）→ X 实例 put 上黑板
           → 应用 Goal 声明的 seedConditions
  ```
- 注册优先级：**显式 Seeder（注解 / 接口 Bean / Contribution）> 自动生成**——同 goalType 有显式者则自动 Seeder 不注册（不是叠加，是让位；trace/日志在 debug 级注明让位）；
- 自动 Seeder 的 `declaredSeedConditions()` 返回 Goal 声明——既有 conformance 守卫恒真兼容；
- 黑板产物**就是 schema record 实例本身**（单对象）。要放多对象或计算条件的场景写显式 Seeder（`SeedResult` 逃生口已有）。

### 裁决 B：类型级转换器——注册到类型，不注册到使用点
```java
// core，com.actiongraph.interpretation.annotation
public interface TypedGoalValueConverter<T> extends GoalValueConverter<T> {
    Class<T> targetType();
}
```
- starter 自动收集所有 `TypedGoalValueConverter` Bean；core 的 Builder 提供 `addConverter(...)`（非 Spring 同权）；
- **解析顺序裁决：使用点显式 `converter` 属性 > 类型注册 > 内置白名单**；
- 同一 targetType 注册两个 → 启动失败报两个来源类名（沿用既有冲突纪律）；
- 转换失败语义不变：`ActionGraphInputException`，消息含参数名 + 原值 + 目标类型。

### 裁决 C：绑定管道抽取为 `GoalParameterBinder`，单一实现两处复用
现有转换逻辑内嵌在 `AnnotatedGoalSeederFactory`（convert/builtInConvert）。抽取为独立的 `GoalParameterBinder`（core，包私有或 `@Internal`）：输入 `GoalParameters + 目标 record 类 + 转换器解析链`，输出实例。自动 Seeder（裁决 A）与注解 Seeder 参数绑定**共用此管道**——两条路径的转换行为物理上不可能分叉。schema 组件上的 `@GoalParameter(required/converter/...)` 语义照旧生效。

### 裁决 D：绑定级校验声明在 schema 上
```java
@GoalSchema(atLeastOne = {"name", "price", "stock", "description", "status"})
record ProductPatch(...) {}
```
- 绑定完成后校验：列出字段全为 null → `ActionGraphInputException`，消息列出候选字段（LLM 可转译为澄清话术）；
- `atLeastOne` 引用不存在的组件名 → 启动期 `ActionGraphConfigurationException`（fail-fast，别等运行时）；
- 首期仅此一条规则；注解留扩展位但不预埋空属性。

### 裁决 E：框架只绑定，不映射业务（永久边界）
自动 Seeding 的终点是 schema record 上黑板。**schema → 业务实体的转换是业务语义，归 Action**。框架永不提供反射式字段拷贝（BeanUtils 之路）。此边界写进 golden-path.md。

### 裁决 F：无参 Goal 自动获得"仅条件"Seeder
Goal 无 schema 且参数声明中无必填项 → 自动注册仅点亮 seedConditions 的 Seeder（registry 内已有同形态内置类，复用之）。`listProducts(){}` 这类空方法仪式从此不需要存在。

---

## 3. 模型与接口（additive 清单，仅限以下）

1. `TypedGoalValueConverter<T>`（新接口，extends `GoalValueConverter<T>`）；
2. `@GoalSchema`（新注解，TYPE 目标，首期唯一属性 `String[] atLeastOne() default {}`）；
3. `GoalParameterBinder`（`@Internal`，抽取自 AnnotatedGoalSeederFactory）；
4. 自动 Seeder 注册逻辑：starter 聚合阶段（显式收集完成后、conformance 核验前）+ core Builder 同等行为；
5. starter 配置：`actiongraph.seeding.auto = true`（默认开；关闭则行为回到 DX3 之前，作为保守逃生口）。

## 4. 验收标准（Definition of Done）

1. **核心场景**：仅声明 `@ActionGraphGoal(schema = X.class)`（无任何 Seeder 代码）→ `actionGraph.start(type, params)` 跑通：黑板含绑定好的 X 实例、种子条件已点亮、链路 COMPLETED。
2. **让位**：同 goalType 存在显式 Seeder → 自动 Seeder 不注册，显式行为不变，黑板无重复对象。
3. **类型转换器**：schema 含领域类型组件（如 `Product`），经注册的 `TypedGoalValueConverter` 解析成功；同类型重复注册 → 启动失败报两来源；使用点显式 `converter` 属性优先于类型注册（测试覆盖三层顺序）。
4. **校验**：`atLeastOne` 全空 → `ActionGraphInputException` 列出候选字段；引用不存在组件 → 启动期配置异常。
5. **无参 Goal**：无 schema、无必填参数、无显式 Seeder → 自动仅条件 Seeder，`start(type, Map.of())` 跑通。
6. **管道统一**：注解 Seeder 的 `@FromGoalParam` 绑定与自动 Seeding 走同一 `GoalParameterBinder`（以测试断言同一异常消息格式佐证）。
7. **dogfood**：samples/renewal 的显式 Seeder 删除、改走自动 Seeding，行为与测试语义零变化；claims 零改动。
8. **概念预算**：`HelloAgentGoldenPathTest` 重写为两注解版（无 Seeder 方法），行数进一步下降；L0 守卫断言更新——`@ActionGraphGoalSeeder` 从"必须含"移除，L0 文案不再出现 Seeder 概念（移至进阶页）。
9. **回归与规范**：423 既有测试全绿（允许断言适配）；`actiongraph.seeding.auto=false` 时行为与 DX3 前完全一致；新 API `@Experimental`；japicmp、文档守卫、`./gradlew build --rerun-tasks` 全绿。

## 5. 实施顺序

C（管道抽取，纯重构先行）→ B（类型注册，地基）→ A（自动 Seeding，核心）→ F（无参場景）→ D（校验）→ dogfood 与文档。

## 6. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 嵌套 record / List 组件绑定 | 不做，遇到即报"不支持的组件类型"配置异常 |
| 2 | 校验规则是否接 Bean Validation (JSR-380) | 不接，atLeastOne 够首期；试点出现第三条规则时再评估 |
| 3 | Optional<T> 组件支持 | 做最小版：Optional 包装等价 required=false |
| 4 | schema 实例上黑板的 BlackboardKey | 默认 key（类型本身）；需要具名 key 属于多对象场景→显式 Seeder |
