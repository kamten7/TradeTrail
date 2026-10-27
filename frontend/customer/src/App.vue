<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from './stores/session'
const route = useRoute()
const router = useRouter()
const auth = useSessionStore()
function expired() {
  auth.user = ''
  auth.checked = false
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
}
onMounted(() => window.addEventListener('tradetrail:unauthenticated', expired))
onUnmounted(() => window.removeEventListener('tradetrail:unauthenticated', expired))
</script>
<template>
  <div class="shop-shell">
    <header class="shop-header">
      <RouterLink to="/products" class="shop-brand">
        <span class="brand-mark">T</span>
        <strong>
          商迹
          <span>TradeTrail</span>
        </strong>
      </RouterLink>
      <span class="shop-header-note">官方好物 · 每一步都可追踪</span>
      <RouterLink to="/account" class="shop-account">
        <van-icon name="user-circle-o" size="24" />
        <span>{{ auth.user || '登录' }}</span>
      </RouterLink>
    </header>
    <main class="shop-content"><RouterView /></main>
    <van-tabbar
      v-if="route.path !== '/login'"
      route
      active-color="var(--tt-primary-active)"
      safe-area-inset-bottom
    >
      <van-tabbar-item replace to="/products" icon="shop-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/cart" icon="shopping-cart-o">购物车</van-tabbar-item>
      <van-tabbar-item replace to="/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/account" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
