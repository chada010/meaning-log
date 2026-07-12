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
  verificationCode: string
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface ResetPasswordRequest {
  email: string
  verificationCode: string
  newPassword: string
}

export const sendVerificationCode = (email: string) => {
  return http.post<void>('/auth/send-code', { email })
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
