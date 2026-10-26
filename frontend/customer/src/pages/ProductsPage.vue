<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, money, type Product } from '../api'
const products = ref<Product[]>([])
const error = ref('')
const busy = ref(false)
async function add(p: Product) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await api('/cart/items', 'POST', { productId: p.id, quantity: 1 }); alert('已加入购物车') }
  catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
onMounted(async () => { try { products.value = await api<Product[]>('/products') } catch (e) { error.value = (e as Error).message } })
</script>
<template><section><h1>官方商城</h1><p v-if="error" role="alert" class="error">{{ error }}</p><p v-if="!products.length">暂无在售商品。</p>
<article v-for="p in products" :key="p.id" class="tt-card"><h2>{{ p.name }}</h2><p>{{ p.description }}</p><div class="row"><strong>{{ money(p.priceCents) }}</strong><span>可售 {{ p.available }}</span><button :disabled="busy || p.available < 1" @click="add(p)">加入购物车</button></div></article>
</section></template>
