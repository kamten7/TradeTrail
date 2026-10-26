<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, label, money, type Order } from '../api'
const orders = ref<Order[]>([])
const error = ref('')
const busy = ref(false)
async function load() { orders.value = await api('/admin/orders') }
async function ship(o: Order) { if (busy.value) return; busy.value = true; error.value = ''; try { await api('/admin/orders/' + o.id + '/shipments', 'POST'); await load() } catch (e) { error.value = (e as Error).message } finally { busy.value = false } }
onMounted(() => load().catch(e => error.value = e.message))
</script>
<template><section class="panel"><div class="row"><h2>订单履约</h2><button @click="load">刷新</button></div><p v-if="error" class="error" role="alert">{{ error }}</p>
<article v-for="o in orders" :key="o.id" class="panel"><div class="row"><strong>订单 #{{ o.id }} · {{ o.customer }}</strong><span>{{ label(o.status) }} · {{ money(o.totalCents) }}</span></div><p v-for="i in o.items" :key="i.productId">{{ i.productName }} × {{ i.quantity }}</p><p>{{ o.address }}</p><p v-if="o.fulfillmentHold">售后已阻断履约，请先处理售后。</p><p v-if="o.trackingNo">运单：{{ o.trackingNo }}</p><button v-if="o.status === 'AWAITING_SHIPMENT'" :disabled="busy || o.fulfillmentHold" @click="ship(o)">发货并生成运单</button></article></section></template>
