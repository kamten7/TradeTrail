<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, label, type AfterSale } from '../api'
import PageHeading from '../components/PageHeading.vue'
import LoadError from '../components/LoadError.vue'
const cases = ref<AfterSale[]>([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const status = ref('')
const page = ref(1)
const dialog = ref(false)
const selected = ref<AfterSale>()
const decision = reactive({ approve: true, reason: '' })
const filtered = computed(() =>
  cases.value.filter((c) => !status.value || c.status === status.value),
)
const rows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
async function load() {
  loading.value = true
  error.value = ''
  try {
    const all: AfterSale[] = []
    for (let batchPage = 0; ; batchPage++) {
      const batch = await api<AfterSale[]>('/admin/after-sales?size=100&page=' + batchPage)
      all.push(...batch)
      if (batch.length < 100) break
    }
    cases.value = all
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
function open(c: AfterSale) {
  selected.value = c
  decision.approve = true
  decision.reason = ''
  dialog.value = true
}
async function decide() {
  if (!selected.value || busy.value) return
  if (!decision.reason.trim()) {
    ElMessage.warning('请填写审核理由')
    return
  }
  busy.value = true
  try {
    await api('/admin/after-sales/' + selected.value.id + '/decisions', 'POST', decision)
    dialog.value = false
    ElMessage.success('审核结果已保存')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function receive(c: AfterSale, qualified: boolean) {
  try {
    await ElMessageBox.confirm(
      qualified
        ? '确认已收到退货且质检合格？库存将回补，并开始退款。'
        : '确认已收到退货但质检不合格？将开始退款，库存不回补。',
      '退货验收',
      { confirmButtonText: '确认验收', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  busy.value = true
  try {
    await api('/admin/after-sales/' + c.id + '/receipts', 'POST', { qualified })
    ElMessage.success('已验收，退款处理中')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <section>
    <PageHeading title="售后管理" description="认真处理每一次申请，让客户服务有据可查。">
      <el-button :loading="loading" @click="load">刷新进度</el-button>
    </PageHeading>
    <div class="metrics">
      <div class="metric metric-accent">
        <div class="metric-label">待审核</div>
        <div class="metric-value">{{ cases.filter((c) => c.status === 'APPLIED').length }}</div>
        <div class="metric-note">需要店家处理的申请</div>
      </div>
      <div class="metric">
        <div class="metric-label">退货处理中</div>
        <div class="metric-value">
          {{
            cases.filter((c) => ['WAITING_RETURN', 'RETURN_IN_TRANSIT'].includes(c.status)).length
          }}
        </div>
        <div class="metric-note">待寄回或待验收</div>
      </div>
      <div class="metric">
        <div class="metric-label">退款处理中</div>
        <div class="metric-value">{{ cases.filter((c) => c.status === 'REFUNDING').length }}</div>
        <div class="metric-note">等待退款确认</div>
      </div>
      <div class="metric">
        <div class="metric-label">退款完成</div>
        <div class="metric-value">{{ cases.filter((c) => c.status === 'REFUNDED').length }}</div>
        <div class="metric-note">已完成的服务记录</div>
      </div>
    </div>
    <LoadError :message="error" @retry="load" />
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-select
          v-model="status"
          placeholder="全部售后状态"
          clearable
          aria-label="售后状态"
          @change="page = 1"
        >
          <el-option
            v-for="s in [
              'APPLIED',
              'WAITING_RETURN',
              'RETURN_IN_TRANSIT',
              'REFUNDING',
              'REFUNDED',
              'REJECTED',
            ]"
            :key="s"
            :label="label(s)"
            :value="s"
          />
        </el-select>
        <span class="toolbar-count">共 {{ filtered.length }} 条售后记录</span>
      </div>
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="售后 / 订单" width="160">
          <template #default="{ row }">
            <strong>售后 #{{ row.id }}</strong>
            <div class="cell-sub">订单 #{{ row.orderId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请内容" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.type === 'REFUND_ONLY' ? '整单退款' : '退货退款' }}</strong>
            <div class="cell-sub">{{ row.reason }}</div>
            <div v-if="row.returnTrackingNo" class="cell-sub">
              退货运单：{{ row.returnTrackingNo }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" min-width="160">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'REFUNDED'
                  ? 'success'
                  : row.status === 'REJECTED'
                    ? 'info'
                    : 'warning'
              "
            >
              {{ label(row.status) }}
            </el-tag>
            <div class="cell-sub">{{ row.decisionReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'APPLIED'" type="primary" link @click="open(row)">
              审核申请
            </el-button>
            <template v-else-if="row.status === 'RETURN_IN_TRANSIT'">
              <el-button type="primary" link :disabled="busy" @click="receive(row, true)">
                质检合格
              </el-button>
              <el-button link :disabled="busy" @click="receive(row, false)">不回补库存</el-button>
            </template>
            <span v-else class="cell-sub">
              {{
                row.status === 'WAITING_RETURN'
                  ? '等待客户寄回'
                  : row.status === 'REFUNDING'
                    ? '等待退款结果'
                    : '已处理'
              }}
            </span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无售后申请" /></template>
      </el-table>
      <div class="table-pagination">
        <span>售后申请期间会暂停订单履约</span>
        <el-pagination
          v-model:current-page="page"
          :total="filtered.length"
          :page-size="10"
          layout="prev, pager, next"
        />
      </div>
    </el-card>
    <el-dialog v-model="dialog" title="审核售后申请" width="520px" :close-on-click-modal="false">
      <p class="dialog-note">订单 #{{ selected?.orderId }} · {{ selected?.reason }}</p>
      <el-form label-width="100px">
        <el-form-item label="审核结果">
          <el-select v-model="decision.approve">
            <el-option :value="true" label="批准申请" />
            <el-option :value="false" label="拒绝申请" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核理由" required>
          <el-input
            v-model="decision.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="请填写清晰的处理理由"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="decide">确认审核</el-button>
      </template>
    </el-dialog>
  </section>
</template>
