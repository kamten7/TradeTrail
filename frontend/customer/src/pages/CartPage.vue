<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api, money, type CartItem, type Quote, type Product } from '../api'
const router = useRouter()
const items = ref<CartItem[]>([])
const products = ref<Product[]>([])
const quotes = ref<Quote[]>([])
const address = ref('')
const error = ref('')
const busy = ref(false)
const total = computed(() => quotes.value.reduce((sum, q) => sum + q.subtotalCents, 0))
async function load() {
  items.value = await api('/cart/items')
  products.value = await api('/products')
  quotes.value = await api('/cart/quote')
}
async function action(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await task() } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function update(item: CartItem) { await action(async () => { await api('/cart/items/' + item.id, 'PUT', { quantity: item.quantity, selected: item.selected }); await load() }) }
async function remove(item: CartItem) { await action(async () => { await api('/cart/items/' + item.id, 'DELETE'); await load() }) }
async function checkout() {
  await action(async () => {
    await api('/cart/checkout', 'POST', { address: address.value, items: quotes.value.map(q => ({ productId: q.productId, quantity: q.quantity, expectedPriceCents: q.unitPriceCents })) })
    await router.push('/orders')
  })
}
onMounted(() => action(load))
</script>
<template><section><h1>购物车与结算</h1><p v-if="error" role="alert" class="error">{{ error }}</p>
<article v-for="item in items" :key="item.id" class="tt-card row"><label><input v-model="item.selected" type="checkbox" :disabled="busy" @change="update(item)">{{ products.find(p => p.id === item.productId)?.name || '商品 #' + item.productId }}</label><label>数量 <input v-model.number="item.quantity" type="number" min="1" max="10000" :disabled="busy" @change="update(item)"></label><button :disabled="busy" @click="remove(item)">移除</button></article>
<form class="tt-card" @submit.prevent="checkout"><h2>确认结算</h2><p v-for="q in quotes" :key="q.productId">{{ q.productName }} × {{ q.quantity }}：{{ money(q.subtotalCents) }} <strong v-if="!q.available">暂不可购买</strong></p><label>收货人、电话及详细地址<textarea v-model="address" required maxlength="500" rows="3"></textarea></label><p>合计 {{ money(total) }}</p><button type="button" :disabled="busy" @click="action(load)">刷新报价</button><button :disabled="busy || !quotes.length || quotes.some(q => !q.available)">确认下单</button></form>
</section></template>
