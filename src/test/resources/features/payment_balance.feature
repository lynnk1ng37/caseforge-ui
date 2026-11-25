# 人工用例：LLS-ORDER-001 立即购买并支付成功（余额支付，实测无需支付密码）

@ui @serial @p0 @positive @LLS-ORDER-001
Feature: 余额支付下单

  作为已登录买家
  我希望用余额完成支付后订单进入待发货
  以便确认支付链路与订单状态流转正确

  Background:
    Given 买家已通过接口登录并注入登录态

  Scenario: 立即购买并用余额支付成功
    When 买家从商品详情页立即购买并提交订单
    And 买家在收银台使用余额支付
    Then 支付完成后应展示支付成功页
    And 我的订单首条应显示待发货
