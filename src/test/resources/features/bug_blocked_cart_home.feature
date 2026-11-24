# 人工用例：LLS-HOME-002 / LLS-CART-001 / LLS-CART-010 的等价可观测版本。
# 背景：原始断言依赖的 UI 元素在当前构建里失效（入口无点击处理、角标不刷新、游客不跳登录），
# 已在 design_checklist.md 的「实现回填」中按 BUG-1~BUG-4 上报。这些场景锁定「当前仍应成立」的行为，
# 避免回归时完全失去覆盖。

@ui @serial @bug-blocked
Feature: 受阻用例的等价断言

  作为买家
  我希望即使局部 UI 缺陷存在，入口与购物车数据仍保持正确
  以便缺陷被修复前仍能发现别的问题

  Background:
    Given 买家已通过接口登录并注入登录态

  @p1 @positive @LLS-HOME-002
  Scenario: 顶部会员与活动入口应可见
    When 买家打开首页并读取顶部入口
    Then 顶部应可读到我的订单入口
    And 顶部应可读到秒杀与优惠券入口

  @p0 @positive @LLS-CART-001
  Scenario: 加购后购物车已选件数应为 1
    Given 买家购物车为空
    When 买家从商品详情页加购测试商品 1 件
    Then 购物车页应显示已选择 1 件商品

  @p0 @exception @LLS-CART-010
  Scenario: 游客访问购物车不报错且登录后数据不丢
    Given 买家购物车已通过造数放入测试商品 1 件
    When 买家清除登录态后访问购物车页
    Then 游客购物车应正常渲染且不展示账号商品
    When 买家重新注入登录态并刷新购物车
    Then 购物车应仍展示该商品
