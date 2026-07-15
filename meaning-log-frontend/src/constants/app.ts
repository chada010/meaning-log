export const AUTH_TOKEN_KEY = 'meaning-log-token'
export const AUTH_USER_KEY = 'meaning-log-user'

export const PENDING_TRIAL_STORAGE_KEY = 'meaning-log-pending-trial'
export const TRIAL_DRAFT_STORAGE_KEY = 'meaning-log-trial-draft'

export const NEW_LOG_DRAFT_STORAGE_KEY = 'meaning-log-draft-new-v2'
export const LEGACY_NEW_LOG_DRAFT_STORAGE_KEY = 'meaning-log-draft-new'
export const EDIT_LOG_DRAFT_STORAGE_KEY_PREFIX = 'meaning-log-draft-edit-'

export const API_TIMEOUT_MS = 30_000
export const AI_CHAT_MESSAGE_MAX_LENGTH = 600
export const SESSION_EXPIRED_RESET_DELAY_MS = 1_000
export const LOGIN_REDIRECT_QUERY_KEY = 'redirect'

export const SERVICE_UNAVAILABLE_MESSAGE = '服务暂时不可用，请稍后再试'
export const INVALID_LOGIN_MESSAGE = '邮箱或密码不正确'
export const AI_UNAVAILABLE_MESSAGE = 'AI 服务暂时不可用，日志仍可正常记录'
export const SESSION_EXPIRED_MESSAGE = '登录状态已过期，请重新登录'

export const getEditLogDraftStorageKey = (id: number) => `${EDIT_LOG_DRAFT_STORAGE_KEY_PREFIX}${id}`
