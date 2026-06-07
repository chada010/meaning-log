import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

const SERVICE_UNAVAILABLE_MESSAGE = '服务暂时不可用，请稍后再试'
const INVALID_LOGIN_MESSAGE = '邮箱或密码不正确'
const AI_UNAVAILABLE_MESSAGE = 'AI 服务暂时不可用，日志仍可正常记录'
const SESSION_EXPIRED_MESSAGE = '登录状态已过期，请重新登录'
const tokenKey = 'meaning-log-token'
const userKey = 'meaning-log-user'

let sessionExpiredNotified = false

const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
})

const getRequestPath = (error: AxiosError) => {
  const url = error.config?.url ?? ''
  const baseURL = error.config?.baseURL ?? ''

  try {
    return new URL(url, baseURL).pathname
  } catch {
    return url
  }
}

const getResponseMessage = (error: AxiosError) => {
  const data = error.response?.data

  if (data && typeof data === 'object' && 'message' in data) {
    const message = data.message
    return typeof message === 'string' ? message : undefined
  }

  return undefined
}

const isAiPath = (path: string) => /\/logs(?:\/\d+)?\/ai(?:\/|$)/.test(path)

const isLoginPath = (path: string) => path.endsWith('/auth/login')

const isAuthExpiredError = (error: AxiosError, path: string) => (
  error.response?.status === 401 &&
  !isLoginPath(path)
)

const isAiDependencyError = (error: AxiosError, responseMessage?: string) => {
  const status = error.response?.status
  const message = responseMessage ?? ''

  return (
    status === 502 ||
    status === 503 ||
    /AI|Redis|rate limiter|DASHSCOPE|OpenAI|api key/i.test(message)
  )
}

const redirectToLogin = (path: string) => {
  localStorage.removeItem(tokenKey)
  localStorage.removeItem(userKey)

  if (window.location.pathname === '/login') {
    return
  }

  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)

  if (path) {
    window.setTimeout(() => {
      sessionExpiredNotified = false
    }, 1000)
  }
}

const getFriendlyErrorMessage = (error: unknown) => {
  if (!axios.isAxiosError(error)) {
    return '请求失败，请稍后重试'
  }

  if (!error.response) {
    return SERVICE_UNAVAILABLE_MESSAGE
  }

  const path = getRequestPath(error)
  const responseMessage = getResponseMessage(error)

  if (isLoginPath(path) && error.response.status === 401) {
    return INVALID_LOGIN_MESSAGE
  }

  if (isAuthExpiredError(error, path)) {
    redirectToLogin(path)
    return SESSION_EXPIRED_MESSAGE
  }

  if (isAiPath(path) && isAiDependencyError(error, responseMessage)) {
    return AI_UNAVAILABLE_MESSAGE
  }

  return responseMessage || error.message || '请求失败，请稍后重试'
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('meaning-log-token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = getFriendlyErrorMessage(error)
    if (message === SESSION_EXPIRED_MESSAGE) {
      if (!sessionExpiredNotified) {
        sessionExpiredNotified = true
        ElMessage.error(message)
      }
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default http
