# 人工用例：<用例编号> <用例标题>

@ui @p0
Feature: <功能名>

  作为<角色>
  我希望<行为>
  以便<业务价值>

  Background:
    Given 买家已通过接口登录并注入登录态

  @positive @<用例编号>
  Scenario: <场景标题>
    When <动作步骤>
    Then <断言步骤>
    And <断言步骤>
