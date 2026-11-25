# 人工用例来源：manual_testcases/02-测试用例设计 gerkin.md（LLS-CART-001/002/004/005/006/007/009/010）
# 不转写：CART-003/011/013（批次 B 造数）、012/014（改价与倒计时）、015（券链路重叠）、016/017（篡改并发）、018（H5）

@ui @serial
Feature: 购物车

  Background:
    Given 买家已通过接口登录并注入登录态

  @p0 @positive @LLS-CART-001 @known-issue
  Scenario: 加购单商品后列表与角标正确
    Given 买家购物车为空
    When 买家从商品详情页加购测试商品 1 件
    Then 购物车角标应显示 1 件商品
    And 购物车应有 1 行商品
    And 该行商品数量应为 1
    And 购物车总价应为 3799 元

  @p0 @positive @LLS-CART-002
  Scenario: 同一 SKU 重复加购数量累加且不新增重复行
    Given 买家购物车已通过造数放入测试商品 1 件
    When 买家再次从商品详情页加购测试商品 1 件
    Then 购物车应有 1 行商品
    And 该行商品数量应为 2
    And 购物车总价应为 7598 元

  @p0 @boundary @LLS-CART-004
  Scenario: 数量递增到库存上限时加号不可再增
    Given 买家购物车已有测试商品 1 件
    When 买家把购物车数量改为该商品库存上限
    Then 购物车数量应不超过库存上限
    When 买家点击购物车数量加号
    Then 购物车数量应仍不超过库存上限
    And 购物车计费数量应不超过库存上限

  @p0 @boundary @LLS-CART-005
  Scenario: 数量输入 0、负数、非数字与超库存被拦截
    Given 买家购物车已有测试商品 1 件
    When 买家把购物车数量改为 0
    Then 购物车数量应回退为 1
    When 买家把购物车数量改为 -1
    Then 购物车数量应回退为 1
    When 买家把购物车数量改为 abc
    Then 购物车数量应回退为 1
    When 买家把购物车数量改为 999999
    Then 购物车总价不应按 999999 计算

  @p1 @boundary @LLS-CART-006
  Scenario: 数量减到 1 时减号置灰且不出现 0
    Given 买家购物车已有测试商品 1 件
    When 买家把购物车数量改为 2
    And 买家点击购物车数量减号
    Then 购物车数量应为 1
    And 购物车数量减号应置灰
    And 购物车小计应等于单价

  @p0 @positive @LLS-CART-007
  Scenario: 勾选或取消勾选后小计与总价实时重算
    Given 买家购物车已有测试商品与另一件低价商品
    Then 购物车总价应等于两件商品金额之和
    When 买家取消勾选第一行商品
    Then 购物车总价应等于剩余勾选商品金额
    When 买家重新勾选第一行商品
    Then 购物车总价应恢复为两件商品金额之和

  @p1 @positive @LLS-CART-009
  Scenario: 删除单个商品与取消清空购物车正确
    Given 买家购物车已有 3 件商品
    When 买家删除购物车第一件商品
    Then 购物车应剩余 2 行商品
    When 买家点击清空购物车并取消确认
    Then 购物车应仍剩余 2 行商品

  @p0 @exception @LLS-CART-010 @known-issue
  Scenario: 未登录访问购物车跳转登录并携带回跳地址
    Given 买家已清除浏览器登录态
    When 买家直接打开购物车页
    Then 应跳转到登录页并携带购物车回跳地址

  # 人工用例：LLS-CART-003 多店铺分组（第二批）
  @p1 @positive @LLS-CART-003
  Scenario: 多店铺商品按店铺分组且不串规格
    Given 买家购物车已有测试商品与另一件低价商品
    When 买家打开购物车页
    Then 购物车应按店铺分组展示
    And 各行商品名应包含各自商品
    And 购物车总价应等于两件商品金额之和

  # 人工用例：LLS-CART-008 全选/全不选/半选（第二批）
  @p1 @exception @LLS-CART-008
  Scenario: 全选、全不选与半选状态正确
    Given 买家购物车已有 3 件商品
    When 买家点击表头全选框
    Then 已勾选商品数应为 0
    When 买家再次点击表头全选框
    Then 已勾选商品数应为 3
    When 买家只勾选第一行商品
    Then 全选框不应处于已全选状态
    And 购物车总价应等于该行商品金额
