import { defineStore } from 'pinia'
import {
  getCurrentUser,
  login,
  register,
  resetPassword,
  type AuthUser,
  type LoginRequest,
  type RegisterRequest,
  type ResetPasswordRequest,
} from '../api/auth'

const tokenKey = 'meaning-log-token'
const userKey = 'meaning-log-user'

const loadUser = (): AuthUser | null => {
  const raw = localStorage.getItem(userKey)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: loadUser(),
    token: localStorage.getItem(tokenKey),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    saveSession(user: AuthUser) {
      this.user = user
      this.token = user.token
      localStorage.setItem(tokenKey, user.token)
      localStorage.setItem(userKey, JSON.stringify(user))
    },
    async register(data: RegisterRequest) {
      const response = await register(data)
      this.saveSession(response.data)
    },
    async login(data: LoginRequest) {
      const response = await login(data)
      this.saveSession(response.data)
    },
    async resetPassword(data: ResetPasswordRequest) {
      await resetPassword(data)
      this.logout()
    },
    async refreshMe() {
      if (!this.token) {
        return
      }

      const response = await getCurrentUser()
      this.saveSession(response.data)
    },
    logout() {
      this.user = null
      this.token = null
      localStorage.removeItem(tokenKey)
      localStorage.removeItem(userKey)
    },
  },
})
