import { post } from './index'

export const authApi = {
  login: (username, password) => post('/api/auth/login', { username, password }),
  register: (username, password) => post('/api/auth/register', { username, password }),
}