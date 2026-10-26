<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { session, login, logout } from './api'
const user = ref('')
const username = ref('customer')
const password = ref('')
const error = ref('')
const busy = ref(false)
const revision = ref(0)
async function refresh() {
  const result = await session()
  user.value = result.roles.includes('ROLE_CUSTOMER') ? result.user : ''
  if (result.user && !user.value) error.value = '当前账号不是用户账号，请切换登录。'
}
async function signIn() {
  busy.value = true; error.value = ''
  try { await login(username.value, password.value); await refresh(); revision.value++ }
  catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function signOut() { await logout(); user.value = ''; revision.value++ }
onMounted(() => refresh().catch(e => error.value = e.message))
</script>
<template>
  <div class="shop-shell">
    <header class="tt-card row"><strong>TradeTrail · 商迹</strong><span>{{ user || '游客' }}</span><button v-if="user" @click="signOut">退出</button></header>
    <nav class="tt-card row"><RouterLink to="/products">商品</RouterLink><RouterLink to="/cart">购物车</RouterLink><RouterLink to="/orders">我的订单</RouterLink></nav>
    <form v-if="!user" class="tt-card" @submit.prevent="signIn"><h2>登录商城</h2><label>账号<input v-model="username" autocomplete="username" required></label><label>密码<input v-model="password" type="password" autocomplete="current-password" required></label><button :disabled="busy">登录</button></form>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <RouterView :key="revision" />
  </div>
</template>
<style>
.shop-shell { max-width: 1040px; margin: auto; padding: 20px; }
label { display: block; margin: 10px 0; }
input, textarea, select { box-sizing: border-box; max-width: 100%; padding: 10px; border: 1px solid #c8ced4; border-radius: 6px; font: inherit; }
button { cursor: pointer; padding: 10px 16px; margin: 4px; border: 1px solid #b9c1c9; border-radius: 6px; background: white; font: inherit; }
button:disabled { opacity: .5; cursor: wait; }
.error { color: #a82020; overflow-wrap: anywhere; }
.row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; justify-content: space-between; }
.tt-card { margin-bottom: 18px; padding: 20px; }
</style>
