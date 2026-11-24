# <页面> 定位器登记表
# 规范：doc/team/locator_policy.md
# by 取值：role / label / placeholder / testid / text / css / xpath
# 级别：L1 role+name / L2 testid|label / L3 业务属性 / L4 语义 class / L5 xpath（需豁免）

- id: <page>.<element>
  page: <page>
  primary: {by: role, role: button, name: "<按钮文案>"}
  fallbacks:
    - {by: text, value: "<按钮文案>"}
  verified_at: "<YYYY-MM-DD>"
  verified_env: "local-127.0.0.1"
  source: probe
  healed_from: null
