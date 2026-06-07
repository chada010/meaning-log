import http from './http'

export interface AuthUser {
  id: number
  email: string
  username: string
  token: string
}

export interface RegisterRequest {
  email: string
  username: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface ResetPasswordRequest {
  email: string
  newPassword: string
}

export const register = (data: RegisterRequest) => {
  return http.post<AuthUser>('/auth/register', data)
}

export const login = (data: LoginRequest) => {
  return http.post<AuthUser>('/auth/login', data)
}

export const resetPassword = (data: ResetPasswordRequest) => {
  return http.post<void>('/auth/reset-password', data)
}

export const getCurrentUser = () => {
  return http.get<AuthUser>('/auth/me')
}
