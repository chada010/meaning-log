# 免费二级域名代理 Quick Tunnel 返回 Cloudflare 1014

## 现象

- `han.zhaisir.com` 是第三方平台分配的免费二级域名，用户不控制一级域名 `zhaisir.com`。
- 将该记录代理到 `*.trycloudflare.com` 后，公网立即返回 `HTTP 403` 与 `error code: 1014`。
- 这不是 DNS 传播延迟：切换前为 521，切换后请求稳定返回 1014。

## 根因

Cloudflare 禁止跨账户的代理 CNAME。免费二级域名所在 Zone 与 Quick Tunnel 不属于同一个 Cloudflare 账户；用户也不能通过添加一级域名 Zone 或修改一级域名 Nameserver 来取得控制权。

## 最小修复

1. Gleam 将 `han.zhaisir.com` 设置为 `A 76.76.21.21`，关闭代理，交给 Vercel 签发 TLS。
2. Vercel 项目绑定 `han.zhaisir.com`。
3. 前端使用相对基址 `/api`。
4. `vercel.json` 将 `/api/:path*` 外部 Rewrite 到当前 Quick Tunnel 的 `/api/:path*`。

## 验证

- HTTPS 首页与 SPA 路由返回 Vercel `200`。
- API POST 返回 Spring 的结构化业务错误与 `X-Trace-Id`，不是 Vercel 405。
- CORS 预检返回 `200` 并允许 `https://han.zhaisir.com`。
- SSE 返回 `Content-Type: text/event-stream`，最终收到 `event: done`。

## 维护边界

Quick Tunnel 重启后 URL 会变化，需要同步更新 `vercel.json` 并重新部署 Vercel。若需要长期稳定且免维护的后端入口，应改用自己控制的域名或提供固定域名的隧道服务。
