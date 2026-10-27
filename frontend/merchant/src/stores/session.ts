import { defineStore } from 'pinia'
import { ref } from 'vue'
import { session, login, logout } from '../api'

export const useSessionStore = defineStore('merchant-session', () => {
  const user = ref('')
  const checking = ref(true)
  const error = ref('')
  const revision = ref(0)
  async function refresh() {
    checking.value = true
    error.value = ''
    try {
      const result = await session()
      user.value = result.roles.includes('ROLE_MERCHANT_ADMIN') ? result.user : ''
      if (result.user && !user.value) error.value = '当前登录的是用户账号，请使用店家账号登录。'
    } catch (cause) {
      error.value = (cause as Error).message
    } finally {
      checking.value = false
    }
  }
  async function signIn(username: string, password: string) {
    error.value = ''
    const result = await login(username, password)
    if (!result.roles.includes('ROLE_MERCHANT_ADMIN'))
      throw new Error('该账号没有店家权限，请使用店家账号。')
    user.value = result.user
    revision.value++
  }
  async function signOut() {
    await logout()
    user.value = ''
    revision.value++
  }
  return { user, checking, error, revision, refresh, signIn, signOut }
})
