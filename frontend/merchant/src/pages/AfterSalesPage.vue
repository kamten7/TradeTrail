<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, label, type AfterSale } from '../api'
const cases = ref<AfterSale[]>([])
const error = ref('')
const busy = ref(false)
async function load() { cases.value = await api('/admin/after-sales?size=100') }
async function action(task: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; try { await task(); await load() } catch (e) { error.value = (e as Error).message } finally { busy.value = false } }
async function decide(c: AfterSale, approve: boolean) { const reason = prompt('请输入审核理由'); if (!reason) return; await action(async () => { await api('/admin/after-sales/' + c.id + '/decisions', 'POST', { approve, reason }) }) }
async function receive(c: AfterSale, qualified: boolean) { if (!confirm(qualified ? '确认已收到退货，质检合格并回补库存？' : '确认已收到退货，质检不合格且不回补库存？')) return; await action(async () => { await api('/admin/after-sales/' + c.id + '/receipts', 'POST', { qualified }) }) }
onMounted(() => action(async () => {}))
</script>
<template><section><div class="row"><h2>售后审核与退货验收</h2><button :disabled="busy" @click="action(async () => {})">刷新</button></div><p v-if="error" class="error" role="alert">{{ error }}</p><article v-for="c in cases" :key="c.id" class="panel"><h3>售后 #{{ c.id }} · 订单 #{{ c.orderId }}</h3><p>{{ c.type === 'REFUND_ONLY' ? '未发货整单退款' : '整单退货退款' }} · {{ label(c.status) }}</p><p>申请原因：{{ c.reason }}</p><p v-if="c.decisionReason">审核理由：{{ c.decisionReason }}</p><p v-if="c.returnTrackingNo">退货运单：{{ c.returnTrackingNo }}</p><template v-if="c.status === 'APPLIED'"><button :disabled="busy" @click="decide(c, true)">批准</button><button :disabled="busy" @click="decide(c, false)">拒绝</button></template><template v-if="c.status === 'RETURN_IN_TRANSIT'"><button :disabled="busy" @click="receive(c, true)">签收并质检合格</button><button :disabled="busy" @click="receive(c, false)">签收但质检不合格</button></template></article></section></template>
