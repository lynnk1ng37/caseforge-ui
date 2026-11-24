# 人工用例来源：manual_testcases/02-测试用例设计 gerkin.md（LLS-HOME-001/002/004/005/006/010）
# 不转写：LLS-HOME-003（运营配置）、007（XSS 安全）、008（店铺内搜索）、009（筛选组件）、011（弱网）、012（后台降级）

@ui @serial
Feature: 首页与搜索

  Background:
    Given 买家已通过接口登录并注入登录态

  @p1 @positive @LLS-HOME-001
  Scenario: 未登录访问首页可浏览且登录入口可用
    Given 买家已清除浏览器登录态
    When 买家打开首页
    Then 首页分类导航应有至少 1 个分类
    And 首页推荐商品应有至少 1 个商品
    And 顶部应展示登录与注册入口

  @p1 @positive @LLS-HOME-002
  Scenario: 顶部购物车与我的订单入口跳转正确
    When 买家点击顶部购物车入口
    Then 购物车页应展示我的购物车步骤条
    When 买家点击顶部我的订单入口
    Then 我的订单页应展示订单页签

  @p0 @positive @LLS-HOME-004
  Scenario: 有效关键字搜索命中商品并可进入详情
    When 买家在搜索框输入一加并提交
    Then 搜索结果应有至少 1 条商品
    And 搜索结果首条商品名应包含一加
    When 买家点击搜索结果首条商品
    Then 商品详情页标题应包含一加 OnePlus 9

  @p1 @boundary @LLS-HOME-005
  Scenario: 搜索关键字空值与长度边界
    When 买家提交空关键字搜索
    Then 空关键字不应被作为检索词提交
    When 买家在搜索框输入单字符 a 并提交
    Then 搜索结果页应展示关键字 a
    When 买家在搜索框输入 60 个字符且不提交
    Then 搜索框保留的字符数应不少于 50

  @p1 @negative @LLS-HOME-006
  Scenario: 无结果关键字展示空态且不残留旧结果
    When 买家搜索不存在的商品关键字
    Then 搜索结果应为 0 条商品

  @p1 @positive @LLS-HOME-010
  Scenario: 秒杀与优惠券入口可进入且活动内容展示
    When 买家点击顶部优惠券入口
    Then 领券中心页应展示领券中心标题
    When 买家点击顶部秒杀入口
    Then 秒杀页应展示限时秒杀
