# 安全政策 / Security policy

本项目仍处于开发阶段；仅当前主分支接受安全修复，不承诺旧开发包的持续维护。

## 报告问题

请勿在公开 Issue 中粘贴 API Key、用户聊天、备份或可直接利用的漏洞细节。
在 GitHub 开启私密漏洞报告后，请使用仓库 Security → Report a vulnerability。
如果该入口尚未开启，请先提交不含细节的“请求私密安全联系渠道”Issue，等待维护者提供渠道。
当前没有独立安全邮箱，也不承诺响应时限或漏洞奖励。

报告应包含：受影响版本、最小复现、权限前提、影响范围和脱敏证据。
发现密钥泄漏时先撤销密钥；删除 Git 文件并不能撤销已泄漏的凭据或清除历史。

## 信任边界

- 模板包、Skill、MCP 服务及模型输出均不是可信代码或可信事实；安装前应核对来源和权限。
- 模板的 SHA-256 校验用于完整性检测，不等于开发者签名或真实性认证。
- 备份密码不被保管；加密不能保护已解锁设备、已授权扩展或接收到上下文的云模型服务。
- API Key 由 Android Keystore 支持的凭据存储管理，不进入可移植备份。
- 默认拒绝明文网络，模板不可直接读取宿主数据库、任意文件或 Provider 密钥。
- 本项目不替代专业安全审计。对外发布前应复核依赖漏洞、许可、签名及 Git 历史中的敏感内容。

## English summary

Only the current development branch receives security fixes. Do not disclose secrets or exploit details in public
issues. Use GitHub private vulnerability reporting when enabled; otherwise request a private contact channel without
disclosing details. No separate security mailbox, response SLA or bounty is currently offered. Revoke leaked keys first.
Template hashes are integrity checks, not signatures. Review extension permissions and cloud-provider privacy policies.
