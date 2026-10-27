<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, label, money, type Order } from '../api'
import PageHeading from '../components/PageHeading.vue'
import LoadError from '../components/LoadError.vue'
const orders = ref<Order[]>([])
const error = ref('')
const loading = ref(true)
const busy = ref(false)
const search = ref('')
const status = ref('')
const page = ref(1)
const selected = ref<Order>()
const visible = ref(false)
const filtered = computed(() =>
  orders.value.filter(
    (o) =>
      (!search.value || (o.id + ' ' + o.customer).includes(search.value)) &&
      (!status.value || o.status === status.value),
  ),
)
const rows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
const count = (state: string) => orders.value.filter((o) => o.status === state).length
async function load() {
  loading.value = true
  error.value = ''
  try {
    orders.value = await api('/admin/orders')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function ship(o: Order) {
  try {
    await ElMessageBox.confirm('确认订单商品已准备完毕？发货后将生成物流运单。', '发货确认', {
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  busy.value = true
  try {
    const result = await api<Order>('/admin/orders/' + o.id + '/shipments', 'POST')
    ElMessage.success('已发货，运单已生成')
    if (selected.value?.id === o.id) selected.value = result
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
function detail(o: Order) {
  selected.value = o
  visible.value = true
}
onMounted(load)
</script>
<template>
  <section>
    <PageHeading title="订单履约" description="从支付确认到包裹发出，及时处理每一份订单。">
      <el-button :loading="loading" @click="load">刷新订单</el-button>
    </PageHeading>
    <div class="metrics">
      <div class="metric metric-accent">
        <div class="metric-label">待发货</div>
        <div class="metric-value">{{ count('AWAITING_SHIPMENT') }}</div>
        <div class="metric-note">付款完成，待安排发货</div>
      </div>
      <div class="metric">
        <div class="metric-label">运输中</div>
        <div class="metric-value">{{ count('SHIPPED') }}</div>
        <div class="metric-note">已发出，待确认收货</div>
      </div>
      <div class="metric">
        <div class="metric-label">履约完成</div>
        <div class="metric-value">{{ count('COMPLETED') }}</div>
        <div class="metric-note">用户已确认或自动完成</div>
      </div>
      <div class="metric">
        <div class="metric-label">售后暂停</div>
        <div class="metric-value">{{ orders.filter((o) => o.fulfillmentHold).length }}</div>
        <div class="metric-note">先处理售后，再继续履约</div>
      </div>
    </div>
    <LoadError :message="error" @retry="load" />
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-input
          v-model="search"
          placeholder="搜索订单号 / 客户"
          clearable
          aria-label="搜索订单"
          @input="page = 1"
        />
        <el-select
          v-model="status"
          placeholder="全部状态"
          clearable
          aria-label="订单状态"
          @change="page = 1"
        >
          <el-option
            v-for="s in [
              'PENDING_PAYMENT',
              'AWAITING_SHIPMENT',
              'SHIPPED',
              'COMPLETED',
              'CLOSED_UNPAID',
              'CLOSED_REFUNDED',
            ]"
            :key="s"
            :label="label(s)"
            :value="s"
          />
        </el-select>
        <span class="toolbar-count">共 {{ filtered.length }} 笔订单</span>
      </div>
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="订单 / 客户" min-width="180">
          <template #default="{ row }">
            <strong>#{{ row.id }}</strong>
            <div class="cell-sub">{{ row.customer }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单商品" min-width="200">
          <template #default="{ row }">
            {{ row.items[0]?.productName }}
            <div class="cell-sub">{{ row.items.length }} 种商品</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="price">{{ money(row.totalCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="164">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'COMPLETED'
                  ? 'success'
                  : row.status === 'AWAITING_SHIPMENT'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ label(row.status) }}
            </el-tag>
            <div v-if="row.fulfillmentHold" class="cell-sub">售后已暂停履约</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="busy || row.fulfillmentHold"
              v-if="row.status === 'AWAITING_SHIPMENT'"
              @click="ship(row)"
            >
              发货
            </el-button>
            <el-button link @click="detail(row)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂时没有符合条件的订单" /></template>
      </el-table>
      <div class="table-pagination">
        <span>订单金额保留成交时快照</span>
        <el-pagination
          v-model:current-page="page"
          :total="filtered.length"
          :page-size="10"
          layout="prev, pager, next"
        />
      </div>
    </el-card>
    <el-dialog v-model="visible" title="订单详情" width="640px">
      <template v-if="selected">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单编号">#{{ selected.id }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ selected.customer }}</el-descriptions-item>
          <el-descriptions-item label="收货信息">{{ selected.address }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">{{ label(selected.status) }}</el-descriptions-item>
          <el-descriptions-item label="运单号">
            {{ selected.trackingNo || '尚未发货' }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-line" v-for="i in selected.items" :key="i.productId">
          <span>{{ i.productName }} × {{ i.quantity }}</span>
          <strong>{{ money(i.unitPriceCents * i.quantity) }}</strong>
        </div>
        <div class="detail-line">
          <span>订单金额</span>
          <strong>{{ money(selected.totalCents) }}</strong>
        </div>
        <el-alert
          v-if="selected.fulfillmentHold"
          title="售后处理中，当前不能发货或确认完成。"
          type="warning"
          :closable="false"
        />
      </template>
    </el-dialog>
  </section>
</template>
