// 打异步 202 端点:HTTP 立刻返回 taskId,真活儿丢给 MQ 消费者。
// 跑法:
//   k6 run -e VUS=10  -e DURATION=15s scripts/loadtest/async-ai.js
//   k6 run -e VUS=50  -e DURATION=60s scripts/loadtest/async-ai.js
//   k6 run -e VUS=100 -e DURATION=60s scripts/loadtest/async-ai.js
// 与 sync-ai.js 相同 VU/DURATION 对比,唯一变量是"HTTP 线程是否被阻塞"。
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, LOG_ID, VUS, DURATION, loginAndGetToken, authHeaders } from './common.js';

export const options = {
  vus: VUS,
  duration: DURATION,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
  thresholds: {
    // 异步 202 只做入库 + 发 MQ,应稳定 <1s
    http_req_duration: ['p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  return { token: loginAndGetToken() };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/logs/${LOG_ID}/ai`,
    null,
    { headers: authHeaders(data.token) }
  );
  check(res, { 'status is 202': (r) => r.status === 202 });
}
