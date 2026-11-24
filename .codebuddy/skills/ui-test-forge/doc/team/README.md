# UI 自动化规范总纲（第二层）

> `SKILL.md` 管「按什么顺序做」，本文件管「必须遵守什么」。
> 编写或修改 UI 用例时**必读**。

## 0. 硬性铁律（7 条，违反即不合格）

1. **未产出 `ui_test_dwp_temp/design_checklist.md` 并等用户确认，不得写用例代码**。
   唯一口令：`确认，按照清单继续`，必须**独立成行**（可附带其它说明行，但同一行不能混写）；改清单后必须重新确认。
2. **用例层禁止直接定位**：不允许 `page.locator/click/fill/navigate/evaluate`，只能调 page object。
3. **禁止固定等待**：不允许 `Thread.sleep` / `page.waitForTimeout`，用 Web-First 断言。
4. **禁止硬编码 URL**：统一走 `src/test/resources/routes.yaml` + `base_url`。
5. **业务断言只在用例层**：page / flow 层只做动作与取值，不写 `assert`。
6. **定位断链只改 `src/test/resources/locators/*.yaml`**，不得删断言、放宽断言让用例变绿。
7. **同一用例 3 轮不通过必须停**，输出最后报错、已尝试策略、归因分类。

## 1. 两层规范

| | skill 内置（通用方法论） | 项目侧（本项目私有） |
|---|---|---|
| 位置 | `.codebuddy/skills/ui-test-forge/` | `locators/`、`docs/domain/` |
| 装什么 | 分层、门禁、模板、探测方法 | 路由、定位器、业务数据前置 |
| 换项目 | 整体复制 | 重新填充 |

判断标准：这份内容复制到别的项目会不会产生误导？会 → 放项目侧。

## 2. 必读文件

| 文件 | 作用 | 时机 |
|---|---|---|
| `doc/preflight_gates.md` | 任务类型 + 输入完整性 + 三阶段 | 入口 |
| `doc/team/case_convert_checklist.md` | 转写清单规范与占比 | 写代码前 |
| `doc/team/locator_policy.md` | 五级定位策略、登记表字段 | 写定位器前 |
| `doc/team/pom_layers.md` | 分层职责与断言边界 | 写 page 前 |
| `doc/team/assertion_layers.md` | UI 断言分层与禁止写法 | 写断言前 |
| `doc/mode_manual_convert.md` | 转写主流程（含登录注入） | 转写任务 |
| `doc/mode_maintenance_heal.md` | 断链维护流程 | 维护任务 |
| `src/test/resources/routes.yaml` | 路由唯一来源 | 打开页面时 |
| `docs/domain/<模块>.md` | 业务规则、数据前置 | 识别出模块后 |

## 3. 分层加载顺序

```
第 1 层 SKILL.md                    工作流骨架
第 2 层 doc/preflight_gates.md      任务类型与输入门禁
        doc/team/README.md          铁律
        doc/team/*.md               定位器 / 分层 / 断言规范
第 3 层 同目录已有用例 1~2 个        风格对齐（只读目标目录）
```

## 4. 物理门禁

| 编号 | 内容 | 触发 |
|---|---|---|
| G2 | 缺清单 | 写 `steps` 且含 `@Then` / `@When` 方法 |
| G3 | 清单未确认 / 确认无日志来源 | 同上 |
| G4 | 场景占比不达标（≥4 条时） | 同上 |
| G5 | 存在 `【待确认】` | 同上 |
| G6 | 用例层裸定位 / 固定等待 / 硬编码 URL | 写 `steps` |
| G7 | page/flow 层业务断言 / 固定等待 | 写 `pages`/`flows` |
| G8 | 用例无断言 | 写 `steps` |
| G9 | 定位器裸 XPath | 写 `locators` |
| G10 | 不同 id 的 primary 选择器重复 | 写 `locators` |

规则唯一事实源：`ui_gate_rules.json`；实现：`tools/check_ui_gate.py`。

## 5. 知识回写

| 动作 | 回写 |
|---|---|
| 新增/修改定位器 | `src/test/resources/locators/*.yaml` 的 `verified_at` / `verified_env` |
| 发现新的业务规则或数据前置 | `docs/domain/<模块>.md` |
| 踩到可复现的坑 | `docs/domain/<模块>.md` 的「已知缺陷」章节（含现象与证据） |
| 新增 hook 或改变浏览器配置 | `hooks/` 与 `config.yaml` 顶部说明 |

## 6. 生成者-验证者闭环

| 角色 | 谁 | 权限 | 产出 |
|---|---|---|---|
| 生成者 | 主 Agent（按本 skill 流程） | 读写代码 | 用例 + page + 定位器 + 自测结果 |
| 验证者 | `.codebuddy/agents/UI审查与执行Agent.md` | **只读 + 运行**（工具白名单无写工具） | PASS / PASS_WITH_WARN / REJECT + 整改清单 |

验证者比人工自查多做的三件事：

1. **连跑 3 次**：单次通过不算通过，2/3 即判 flake；
2. **读自愈记录**：`UI_HEAL_LOG` 落盘的 JSONL 里出现记录即降级为 `PASS_WITH_WARN`；
3. **反向覆盖定位器**：页面里用到的每个 `locator(page, "<id>")` 必须在 YAML 里登记。

REJECT 后生成者按清单整改，同一用例最多 3 轮；3 轮仍不过必须上报，不得无限试错。

## 7. 移植到新项目

1. 复制整个 `.codebuddy/skills/ui-test-forge/`
2. 改 `config.json` 的 `project_root`、`base_url_key`、`paths`
3. 重建 `src/test/resources/locators/*.yaml` 与 `docs/domain/`
4. 在 `.codebuddy/settings.json` 注册三个 hook
