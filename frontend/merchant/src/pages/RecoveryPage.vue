<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
type Event = { eventId: string; type: string; aggregateId: string; attempts: number; lastError: string }
const events = ref<Event[]>([])
const error = ref('')
const busy = ref(false)
async function load() { try { events.value = await api('/admin/outbox') } catch (e) { error.value = (e as Error).message } }
async function retry(e: Event) { busy.value = true; try { await api('/admin/outbox/' + e.eventId + '/retry', 'POST'); await load() } catch (cause) { error.value = (cause as Error).message } finally { busy.value = false } }
onMounted(load)
</script>
<template><section class="panel"><div class="row"><h2>异常恢复</h2><button @click="load">刷新</button></div><p>自动重试达到上限的任务会出现在这里。排除故障后可重新投递。</p><p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="!events.length">暂无待处理任务。</p><article v-for="e in events" :key="e.eventId" class="panel"><p>{{ e.type === 'REFUND_EXECUTE' ? '退款处理' : e.type === 'PAYMENT_NOTICE' ? '支付确认' : '业务通知' }} · {{ e.aggregateId }}</p><p>尝试 {{ e.attempts }} 次 · {{ e.lastError }}</p><button :disabled="busy" @click="retry(e)">重新处理</button></article></section></template>
