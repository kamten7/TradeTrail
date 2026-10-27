<script setup lang="ts">
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { useSessionStore } from '../stores/session'
const auth = useSessionStore()
const router = useRouter()
async function signOut() {
  try {
    await auth.signOut()
    await router.replace('/products')
  } catch (e) {
    showFailToast((e as Error).message)
  }
}
</script>
<template>
  <section>
    <div class="account-card">
      <div class="account-avatar"><van-icon name="user-o" size="32" /></div>
      <h1>{{ auth.user || '欢迎来到商迹' }}</h1>
      <p class="muted">
        {{ auth.user ? '你的每一笔订单，都有迹可循。' : '登录后查看订单、物流与售后进度。' }}
      </p>
      <van-button v-if="!auth.user" type="primary" to="/login" class="form-submit">
        登录账号
      </van-button>
    </div>
    <van-cell-group inset>
      <van-cell
        title="我的订单"
        label="付款、物流与售后进度"
        icon="orders-o"
        is-link
        to="/orders"
      />
      <van-cell title="购物车" label="查看选中的商品" icon="shopping-cart-o" is-link to="/cart" />
    </van-cell-group>
    <van-button v-if="auth.user" block plain class="form-submit" @click="signOut">
      退出登录
    </van-button>
  </section>
</template>
