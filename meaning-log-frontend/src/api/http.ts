import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import {
  AI_UNAVAILABLE_MESSAGE,
  API_TIMEOUT_MS,
  AUTH_TOKEN_KEY,
  AUTH_USER_KEY,
  INVALID_LOGIN_MESSAGE,
  LOGIN_REDIRECT_QUERY_KEY,
  SERVICE_UNAVAILABLE_MESSAGE,
  SESSION_EXPIRED_MESSAGE,
  SESSION_EXPIRED_RESET_DELAY_MS,
} from '../constants/app'

let sessionExpiredNotified = false

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: API_TIMEOUT_MS,
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

const isAiPath = (path: string) => (
  /\/logs(?:\/\d+)?\/ai(?:\/|$)/.test(path) ||
  path.endsWith('/trial/analyze')
)

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
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(AUTH_USER_KEY)

  if (window.location.pathname === '/login') {
    return
  }

  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.assign(`/login?${LOGIN_REDIRECT_QUERY_KEY}=${encodeURIComponent(redirect)}`)

  if (path) {
    window.setTimeout(() => {
      sessionExpiredNotified = false
    }, SESSION_EXPIRED_RESET_DELAY_MS)
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
  const token = localStorage.getItem(AUTH_TOKEN_KEY)

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
