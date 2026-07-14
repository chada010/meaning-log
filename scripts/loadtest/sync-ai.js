// 打同步 baseline 端点(压测 profile 专属):HTTP 线程被 5s 上游 sleep 卡死,模拟 Track 2 引入 MQ 前的旧版行为。
// 跑法:
//   k6 run -e VUS=10  -e DURATION=15s scripts/loadtest/sync-ai.js
//   k6 run -e VUS=50  -e DURATION=60s scripts/loadtest/sync-ai.js
//   k6 run -e VUS=100 -e DURATION=60s scripts/loadtest/sync-ai.js
// 与 async-ai.js 相同 VU/DURATION 对比,唯一变量是"HTTP 线程是否被阻塞"。
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, LOG_ID, VUS, DURATION, loginAndGetToken, authHeaders } from './common.js';

export const options = {
  vus: VUS,
  duration: DURATION,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
  thresholds: {
    // 同步接口预期高错误率(Tomcat 线程池打满 → 503 / 超时),不设硬门槛
    http_req_failed: ['rate<0.99'],
  },
};

export function setup() {
  return { token: loginAndGetToken() };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/loadtest/logs/${LOG_ID}/ai-sync`,
    null,
    {
      headers: authHeaders(data.token),
      timeout: '60s',
    }
  );
  check(res, { 'status is 200': (r) => r.status === 200 });
}
