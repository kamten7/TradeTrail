<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'
const auth = useSessionStore()
const route = useRoute()
const router = useRouter()
const username = ref('customer')
const password = ref('')
const busy = ref(false)
const error = ref(auth.error)
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await auth.signIn(username.value, password.value)
    const redirect = String(route.query.redirect || '/products')
    await router.replace(
      ['/cart', '/orders', '/account'].includes(redirect) ? redirect : '/products',
    )
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="customer-login">
    <div class="login-symbol"><van-icon name="shop-o" size="32" /></div>
    <h1>欢迎来到商迹</h1>
    <p>登录后选购好物，随时查看订单旅程。</p>
    <div v-if="error" role="alert" class="error-panel">
      <p>{{ error }}</p>
    </div>
    <van-form @submit="submit">
      <van-field
        v-model="username"
        name="username"
        label="账号"
        autocomplete="username"
        placeholder="请输入用户账号"
        :rules="[{ required: true, message: '请输入账号' }]"
      />
      <van-field
        v-model="password"
        name="password"
        label="密码"
        type="password"
        autocomplete="current-password"
        placeholder="请输入密码"
        :rules="[{ required: true, message: '请输入密码' }]"
      />
      <van-button type="primary" block native-type="submit" :loading="busy" class="form-submit">
        登录商城
      </van-button>
    </van-form>
    <van-button plain block class="form-submit" to="/products">先逛逛商品</van-button>
  </section>
</template>
