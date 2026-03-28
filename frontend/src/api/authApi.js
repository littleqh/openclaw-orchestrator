import { post } from './index'

export const authApi = {
  login: (username, password) => post('/auth/login', { username, password }),
  register: (username, password) => post('/auth/register', { username, password }),
}