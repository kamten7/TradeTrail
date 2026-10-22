<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, Button as VanButton, Card as VanCard, Field as VanField, Tabbar as VanTabbar, TabbarItem as VanTabbarItem, Tag as VanTag } from 'vant'
import 'vant/lib/index.css'
import { api, login, session, type Product, type Order, type Tracking, type CartItem } from './api'

const route = useRoute()
const router = useRouter()
const user = ref('')
const products = ref<Product[]>([])
const orders = ref<Order[]>([])
const cart = ref<CartItem[]>([])
const timelines = ref<Record<number, Tracking[]>>({})
const username = ref('customer')
const password = ref('customer123')
const afterSaleReason = ref('')
const loading = ref(false)
const error = ref('')
const active = computed(() => route.path.startsWith('/orders') ? 'orders' : route.path.startsWith('/cart') ? 'cart' : 'products')
const money = (value: number) => `¥${(value / 100).toFixed(2)}`

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    user.value = await session()
    products.value = await api<Product[]>('/products')
    orders.value = user.value ? await api<Order[]>('/orders') : []
    cart.value = user.value ? await api<CartItem[]>('/cart/items') : []
  } catch (cause) { error.value = (cause as Error).message }
  finally { loading.value = false }
}
async function signIn() {
  if (!await login(username.value, password.value)) { error.value = '登录失败，请检查账号密码'; return }
  await refresh()
}
async function buy(productId: number, quantity = 1) {
  if (!user.value) { error.value = '请先登录'; return }
  try {
    await api<Order>('/orders', 'POST', { productId, quantity }, crypto.randomUUID())
    showToast('下单成功')
    await refresh()
    await router.push('/orders')
  } catch (cause) { error.value = (cause as Error).message }
}
async function addToCart(product: Product) {
  if (!user.value) { error.value = '请先登录'; return }
  try { await api('/cart/items', 'POST', { productId: product.id, quantity: 1 }); showToast('已加入购物车'); await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
async function removeFromCart(item: CartItem) {
  try { await api(`/cart/items/${item.id}`, 'DELETE'); await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
async function pay(order: Order) {
  try { await api(`/orders/${order.id}/payments`, 'POST'); showToast('模拟支付成功'); await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
async function track(order: Order) {
  try { timelines.value[order.id] = await api<Tracking[]>(`/orders/${order.id}/tracking`) }
  catch (cause) { error.value = (cause as Error).message }
}
async function applyAfterSale(order: Order) {
  if (!afterSaleReason.value.trim()) { error.value = '请填写售后原因'; return }
  try { await api(`/orders/${order.id}/after-sales`, 'POST', { reason: afterSaleReason.value }); showToast('售后申请已提交'); afterSaleReason.value = '' }
  catch (cause) { error.value = (cause as Error).message }
}
onMounted(refresh)
</script>

<template>
  <div class="tt-page">
    <header class="hero"><div><strong>TradeTrail 商迹</strong><p>从下单到签收，每一步都看得见。</p></div><span>{{ user || '访客' }}</span></header>
    <section v-if="!user" class="tt-card login"><h2>登录后下单</h2><VanField v-model="username" label="账号" /><VanField v-model="password" label="密码" type="password" /><VanButton type="primary" block @click="signIn">登录</VanButton></section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="loading" class="hint">加载中…</p>
    <main v-else>
      <section v-if="active === 'products'"><h1>精选商品</h1><p v-if="products.length === 0" class="hint">暂无上架商品，店家发布后会显示在这里。</p>
        <VanCard v-for="product in products" :key="product.id" :title="product.name" :desc="product.description" :price="(product.priceCents / 100).toFixed(2)" class="product">
          <template #tags><VanTag type="success">库存 {{ product.available }}</VanTag></template>
          <template #footer><VanButton size="small" :disabled="product.available < 1" @click="addToCart(product)">加购物车</VanButton><VanButton size="small" type="primary" :disabled="product.available < 1" @click="buy(product.id)">立即购买</VanButton></template>
        </VanCard>
      </section>
      <section v-else-if="active === 'cart'"><h1>购物车</h1><p v-if="cart.length === 0" class="hint">购物车是空的，去看看商品吧。</p><article v-for="item in cart" :key="item.id" class="tt-card order"><div class="row"><strong>{{ products.find(product => product.id === item.productId)?.name || `商品 #${item.productId}` }}</strong><span>× {{ item.quantity }}</span></div><VanButton size="small" type="primary" @click="buy(item.productId, item.quantity)">下单</VanButton><VanButton size="small" @click="removeFromCart(item)">移除</VanButton></article></section>
      <section v-else><h1>我的订单</h1><p v-if="!user || orders.length === 0" class="hint">暂无订单。</p>
        <article v-for="order in orders" :key="order.id" class="tt-card order"><div class="row"><strong>订单 #{{ order.id }}</strong><VanTag type="primary">{{ order.status }}</VanTag></div><p>商品 #{{ order.productId }} · {{ order.quantity }} 件</p><p class="tt-price">{{ money(order.totalCents) }}</p>
          <p v-if="order.trackingNo" class="hint">运单 {{ order.trackingNo }}</p><VanButton v-if="order.status === 'PENDING_PAYMENT'" size="small" type="primary" @click="pay(order)">模拟付款</VanButton><VanButton v-if="order.trackingNo" size="small" @click="track(order)">查看物流</VanButton>
          <div v-if="order.status === 'PAID' || order.status === 'SHIPPED'" class="after-sale"><VanField v-model="afterSaleReason" label="售后原因" placeholder="请填写原因" /><VanButton size="small" @click="applyAfterSale(order)">申请售后</VanButton></div>
          <ol v-if="timelines[order.id]?.length" class="timeline"><li v-for="event in timelines[order.id]" :key="event.eventId">{{ event.status }} · {{ event.location }} · {{ event.occurredAt }}</li></ol>
        </article>
      </section>
    </main>
    <VanTabbar :model-value="active" @change="(value: string) => router.push('/' + value)"><VanTabbarItem name="products" icon="shop-o">商品</VanTabbarItem><VanTabbarItem name="cart" icon="shopping-cart-o">购物车</VanTabbarItem><VanTabbarItem name="orders" icon="orders-o">订单</VanTabbarItem></VanTabbar>
  </div>
</template>
