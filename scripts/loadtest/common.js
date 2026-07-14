// 复用逻辑:环境变量读取 + 登录换 token
// 所有配置项支持通过 k6 -e 覆盖,例:k6 run -e VUS=50 -e DURATION=60s sync-ai.js
import http from 'k6/http';
import { fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const USER_EMAIL = __ENV.USER_EMAIL || 'test';
export const USER_PASSWORD = __ENV.USER_PASSWORD || 'test1234';
export const LOG_ID = Number(__ENV.LOG_ID || 1);
export const VUS = Number(__ENV.VUS || 10);
export const DURATION = __ENV.DURATION || '30s';

// 登录拿 JWT。setup() 阶段执行一次,后续所有 VU 共用同一个 token。
export function loginAndGetToken() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ identifier: USER_EMAIL, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    fail(`login failed: HTTP ${res.status} body=${res.body}`);
  }
  const token = res.json('token');
  if (!token) {
    fail(`login response missing token: ${res.body}`);
  }
  return token;
}

export function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}
