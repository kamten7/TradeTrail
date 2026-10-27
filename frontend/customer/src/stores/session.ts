import { defineStore } from 'pinia'
import { ref } from 'vue'
import { session, login, logout } from '../api'
export const useSessionStore = defineStore('customer-session', () => {
  const user = ref('')
  const checked = ref(false)
  const error = ref('')
  async function refresh() {
    error.value = ''
    try {
      const result = await session()
      user.value = result.roles.includes('ROLE_CUSTOMER') ? result.user : ''
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      checked.value = true
    }
  }
  async function signIn(username: string, password: string) {
    const result = await login(username, password)
    if (!result.roles.includes('ROLE_CUSTOMER')) throw new Error('请使用商城用户账号登录。')
    user.value = result.user
    checked.value = true
  }
  async function signOut() {
    await logout()
    user.value = ''
    checked.value = false
  }
  return { user, checked, error, refresh, signIn, signOut }
})
