<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { session, login, logout } from './api'
const user = ref('')
const username = ref('merchant')
const password = ref('')
const busy = ref(false)
const error = ref('')
async function refresh() { const s = await session(); user.value = s.roles.includes('ROLE_MERCHANT_ADMIN') ? s.user : ''; if (s.user && !user.value) error.value = '请使用店家账号登录。' }
async function signIn() { busy.value = true; error.value = ''; try { await login(username.value, password.value); await refresh() } catch (e) { error.value = (e as Error).message } finally { busy.value = false } }
async function signOut() { await logout(); user.value = '' }
onMounted(() => refresh().catch(e => error.value = e.message))
</script>
<template><div class="merchant-shell"><header class="row panel"><h1>TradeTrail 店家管理台</h1><span>{{ user }}</span><button v-if="user" @click="signOut">退出</button></header>
<nav v-if="user" class="row panel"><RouterLink to="/products">商品与库存</RouterLink><RouterLink to="/orders">订单履约</RouterLink><RouterLink to="/after-sales">售后审核</RouterLink><RouterLink to="/recovery">异常恢复</RouterLink></nav>
<p v-if="error" class="error" role="alert">{{ error }}</p><form v-if="!user" class="panel" @submit.prevent="signIn"><h2>店家登录</h2><label>账号<input v-model="username" autocomplete="username" required></label><label>密码<input v-model="password" autocomplete="current-password" type="password" required></label><button :disabled="busy">登录</button></form><RouterView v-else /></div></template>
<style>
.merchant-shell { max-width: 1200px; margin: auto; padding: 24px; }
.panel { padding: 24px; background: white; margin-bottom: 20px; border: 1px solid #e0e4e8; border-radius: 10px; }
.row { display: flex; gap: 16px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
label { display: block; margin: 12px 0; }
input, textarea, select { padding: 10px; margin-left: 8px; max-width: 100%; border: 1px solid #c3cbd3; border-radius: 6px; font: inherit; box-sizing: border-box; }
button { margin: 4px; padding: 10px 14px; border: 1px solid #b8c2cb; border-radius: 6px; background: white; font: inherit; cursor: pointer; }
button:disabled { opacity: .5; cursor: wait; }
.error { color: #a82020; overflow-wrap: anywhere; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: left; border-bottom: 1px solid #e0e4e8; padding: 12px; }
.table-scroll { overflow-x: auto; }
</style>
