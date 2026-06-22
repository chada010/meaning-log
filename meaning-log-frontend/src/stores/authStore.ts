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
import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from '../constants/app'

const loadUser = (): AuthUser | null => {
  const raw = localStorage.getItem(AUTH_USER_KEY)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: loadUser(),
    token: localStorage.getItem(AUTH_TOKEN_KEY),
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
  },
  actions: {
    saveSession(user: AuthUser) {
      this.user = user
      this.token = user.token
      localStorage.setItem(AUTH_TOKEN_KEY, user.token)
      localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
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
      localStorage.removeItem(AUTH_TOKEN_KEY)
      localStorage.removeItem(AUTH_USER_KEY)
    },
  },
})
