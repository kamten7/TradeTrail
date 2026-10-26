<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, money, label, type Order, type Payment, type PaymentView, type AfterSale, type Tracking } from '../api'
const orders = ref<Order[]>([])
const details = ref<Record<number, PaymentView>>({})
const cases = ref<Record<number, AfterSale>>({})
const timelines = ref<Record<number, Tracking[]>>({})
const error = ref('')
const busy = ref(false)
const page = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
async function load() {
  orders.value = await api<Order[]>('/orders?page=' + page.value)
  for (const o of orders.value) {
    details.value[o.id] = await api<PaymentView>('/orders/' + o.id + '/payments')
    try { cases.value[o.id] = await api<AfterSale>('/orders/' + o.id + '/after-sales') }
    catch (e) { if ((e as { code?: string }).code !== 'AFTER_SALE_NOT_FOUND') throw e }
  }
}
async function action(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await task(); await load() } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function pay(o: Order) {
  await action(async () => {
    const payment = await api<Payment>('/orders/' + o.id + '/payments', 'POST')
    await api('/mock/checkout/' + payment.id + '/complete', 'POST', { success: true })
  })
}
async function apply(o: Order) {
  const reason = prompt('请填写售后原因：'); if (!reason) return
  await action(async () => { await api('/orders/' + o.id + '/after-sales', 'POST', { reason }) })
}
async function sendReturn(c: AfterSale) {
  const trackingNo = prompt('请输入退货运单号：'); if (!trackingNo) return
  await action(async () => { await api('/after-sales/' + c.id + '/return', 'POST', { trackingNo }) })
}
async function track(o: Order) { await action(async () => { timelines.value[o.id] = await api<Tracking[]>('/orders/' + o.id + '/tracking') }) }
async function move(delta: number) { page.value += delta; await action(async () => {}) }
onMounted(() => {
  void action(async () => {})
  timer = setInterval(() => { if (!busy.value && orders.value.some(o => o.status === 'PENDING_PAYMENT' || cases.value[o.id]?.status === 'REFUNDING')) void action(async () => {}) }, 4000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template><section><div class="row"><h1>我的订单</h1><button :disabled="busy" @click="action(async () => {})">刷新</button></div><p v-if="error" role="alert" class="error">{{ error }}</p>
<article v-for="o in orders" :key="o.id" class="tt-card"><div class="row"><h2>订单 #{{ o.id }}</h2><strong>{{ label(o.status) }}</strong></div>
<p v-for="i in o.items" :key="i.productId">{{ i.productName }} × {{ i.quantity }} · {{ money(i.unitPriceCents) }}</p><p>收货地址：{{ o.address }}</p><p>总额 {{ money(o.totalCents) }}</p><p v-if="o.fulfillmentHold">售后处理中，履约已暂停。</p>
<p v-if="details[o.id]?.payment.status">支付：{{ label(details[o.id].payment.status!) }}</p><p v-if="details[o.id]?.refund.status">退款：{{ label(details[o.id].refund.status!) }} {{ money(details[o.id].refund.amountCents || 0) }}</p>
<button v-if="o.status === 'PENDING_PAYMENT'" :disabled="busy" @click="pay(o)">模拟付款</button><button v-if="o.status === 'PENDING_PAYMENT'" :disabled="busy" @click="action(async () => { await api('/orders/' + o.id + '/cancel', 'POST') })">取消订单</button>
<button v-if="o.status === 'SHIPPED' && !o.fulfillmentHold" :disabled="busy" @click="action(async () => { await api('/orders/' + o.id + '/confirm', 'POST') })">确认收货</button>
<button v-if="['AWAITING_SHIPMENT','SHIPPED','COMPLETED'].includes(o.status) && !cases[o.id]" :disabled="busy" @click="apply(o)">申请售后</button>
<div v-if="cases[o.id]"><p>售后：{{ label(cases[o.id].status) }} · {{ cases[o.id].decisionReason }}</p><button v-if="cases[o.id].status === 'WAITING_RETURN'" :disabled="busy" @click="sendReturn(cases[o.id])">填写退货运单</button></div>
<p v-if="o.trackingNo">运单 {{ o.trackingNo }} <button :disabled="busy" @click="track(o)">查看物流</button></p><ol><li v-for="t in timelines[o.id]" :key="t.eventId">{{ label(t.status) }} · {{ t.location }} · {{ t.occurredAt }}</li></ol>
</article><div class="row"><button :disabled="busy || page === 0" @click="move(-1)">上一页</button><span>第 {{ page + 1 }} 页</span><button :disabled="busy || orders.length < 20" @click="move(1)">下一页</button></div>
</section></template>
