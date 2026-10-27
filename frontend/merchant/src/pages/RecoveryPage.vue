<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, money, type Refund } from '../api'
import PageHeading from '../components/PageHeading.vue'
import LoadError from '../components/LoadError.vue'
type Event = {
  eventId: string
  type: string
  aggregateId: string
  attempts: number
  lastError: string
}
const events = ref<Event[]>([])
type FailedRefund = Refund & { orderId: number; attempts: number; lastError: string }
const refunds = ref<FailedRefund[]>([])
const error = ref('')
const loading = ref(true)
const busy = ref('')
const title = (type: string) =>
  ({
    REFUND_EXECUTE: '退款处理',
    PAYMENT_NOTICE: '支付确认',
    REFUND_SUCCEEDED: '退款状态同步',
    ORDER_CHANGED: '订单状态同步',
  })[type] || '业务通知'
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [queued, failed] = await Promise.all([
      api<Event[]>('/admin/outbox'),
      api<FailedRefund[]>('/admin/refunds/failed'),
    ])
    events.value = queued
    refunds.value = failed
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function retry(e: Event) {
  busy.value = e.eventId
  try {
    await api('/admin/outbox/' + e.eventId + '/retry', 'POST')
    ElMessage.success('已重新安排处理')
    await load()
  } catch (cause) {
    ElMessage.error((cause as Error).message)
  } finally {
    busy.value = ''
  }
}
onMounted(load)
async function retryRefund(refund: FailedRefund) {
  if (busy.value) return
  busy.value = refund.id
  try {
    await api('/admin/refunds/' + refund.id + '/retry', 'POST')
    ElMessage.success('已重新提交退款，请到售后管理查看结果')
    await load()
  } catch (cause) {
    ElMessage.error((cause as Error).message)
  } finally {
    busy.value = ''
  }
}
</script>
<template>
  <section>
    <PageHeading title="异常恢复" description="集中处理自动重试后仍未完成的业务任务。">
      <el-button :loading="loading" @click="load">刷新状态</el-button>
    </PageHeading>
    <LoadError :message="error" @retry="load" />
    <el-alert
      title="先确认相关服务已恢复，再重新处理。系统会沿用原业务编号，避免重复操作。"
      type="info"
      :closable="false"
      show-icon
      class="load-error"
    />
    <el-card class="table-card">
      <template #header>待重试退款</template>
      <el-table v-loading="loading" :data="refunds" stripe>
        <el-table-column label="订单" width="140">
          <template #default="{ row }">订单 #{{ row.orderId }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="140">
          <template #default="{ row }">{{ money(row.amountCents) }}</template>
        </el-table-column>
        <el-table-column prop="lastError" label="失败原因" min-width="240" />
        <el-table-column prop="attempts" label="尝试次数" width="100" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="!!busy"
              :loading="busy === row.id"
              @click="retryRefund(row)"
            >
              重试退款
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无失败的退款" /></template>
      </el-table>
    </el-card>
    <el-card class="table-card" style="margin-top: 24px">
      <template #header>待恢复任务</template>
      <el-table v-loading="loading" :data="events" stripe>
        <el-table-column label="任务" min-width="160">
          <template #default="{ row }">
            <strong>{{ title(row.type) }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="aggregateId" label="业务编号" min-width="220" />
        <el-table-column prop="attempts" label="尝试次数" width="100" align="right" />
        <el-table-column label="失败原因" min-width="220">
          <template #default="{ row }">
            <span class="cell-sub">{{ row.lastError }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :loading="busy === row.eventId" @click="retry(row)">
              重新处理
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="recovery-empty">
            <el-empty description="暂无待处理的异常任务">
              <el-tag type="success">无需人工重试任务</el-tag>
            </el-empty>
          </div>
        </template>
      </el-table>
    </el-card>
  </section>
</template>
