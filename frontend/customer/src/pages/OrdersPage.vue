<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { showConfirmDialog, showFailToast, showSuccessToast, showToast } from 'vant'
import {
  api,
  money,
  localTime,
  label,
  type Order,
  type Payment,
  type PaymentView,
  type AfterSale,
  type Tracking,
} from '../api'
const orders = ref<Order[]>([])
const details = ref<Record<number, PaymentView>>({})
const cases = ref<Record<number, AfterSale>>({})
const error = ref('')
const loading = ref(true)
const busy = ref(false)
const page = ref(0)
const active = ref('all')
const popup = ref(false)
const popupMode = ref<'apply' | 'return' | 'tracking'>('apply')
const selected = ref<Order>()
const input = ref('')
const timeline = ref<Tracking[]>([])
const trackingLoading = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
let disposed = false
const visible = computed(() =>
  orders.value.filter((o) => active.value === 'all' || o.status === active.value),
)
const color = (status: string) =>
  status === 'COMPLETED' || status === 'REFUNDED'
    ? 'success'
    : status === 'PENDING_PAYMENT' || status === 'AWAITING_SHIPMENT'
      ? 'warning'
      : 'primary'
async function load(quiet = false) {
  if (!quiet) {
    loading.value = true
    error.value = ''
  }
  try {
    const result = await api<Order[]>('/orders?page=' + page.value)
    if (disposed) return
    orders.value = result
    for (const o of result) {
      if (disposed) return
      details.value[o.id] = await api<PaymentView>('/orders/' + o.id + '/payments')
      if (['AWAITING_SHIPMENT', 'SHIPPED', 'COMPLETED', 'CLOSED_REFUNDED'].includes(o.status)) {
        try {
          cases.value[o.id] = await api<AfterSale>('/orders/' + o.id + '/after-sales')
        } catch (e) {
          if ((e as { code?: string }).code !== 'AFTER_SALE_NOT_FOUND') throw e
        }
      }
    }
  } catch (e) {
    if (!disposed) error.value = (e as Error).message
  } finally {
    if (!disposed) loading.value = false
  }
}
async function action(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  try {
    await task()
    await load(true)
  } catch (e) {
    showFailToast((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function pay(o: Order) {
  try {
    await showConfirmDialog({
      title: '模拟支付',
      message: '确认支付 ' + money(o.totalCents) + '？这是演示付款，不会扣取真实资金。',
      confirmButtonText: '确认付款',
    })
  } catch {
    return
  }
  await action(async () => {
    const payment = await api<Payment>('/orders/' + o.id + '/payments', 'POST')
    await api('/mock/checkout/' + payment.id + '/complete', 'POST', { success: true })
    showToast('付款已提交，正在确认支付结果')
  })
}
async function cancel(o: Order) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确认取消这笔尚未支付的订单？' })
  } catch {
    return
  }
  await action(async () => {
    await api('/orders/' + o.id + '/cancel', 'POST')
    showSuccessToast('订单已取消')
  })
}
async function confirm(o: Order) {
  try {
    await showConfirmDialog({
      title: '确认收货',
      message: '请确认已收到包裹。确认后仍可在售后期限内申请售后。',
    })
  } catch {
    return
  }
  await action(async () => {
    await api('/orders/' + o.id + '/confirm', 'POST')
    showSuccessToast('已确认收货')
  })
}
function open(o: Order, mode: 'apply' | 'return') {
  selected.value = o
  popupMode.value = mode
  input.value = ''
  popup.value = true
}
async function submitPopup() {
  if (!selected.value || !input.value.trim()) {
    showFailToast(popupMode.value === 'apply' ? '请填写售后原因' : '请填写退货运单号')
    return
  }
  await action(async () => {
    const o = selected.value!
    if (popupMode.value === 'apply')
      await api('/orders/' + o.id + '/after-sales', 'POST', { reason: input.value })
    else
      await api('/after-sales/' + cases.value[o.id].id + '/return', 'POST', {
        trackingNo: input.value,
      })
    popup.value = false
    showSuccessToast('已提交')
  })
}
async function track(o: Order) {
  selected.value = o
  popupMode.value = 'tracking'
  popup.value = true
  timeline.value = []
  trackingLoading.value = true
  try {
    timeline.value = (await api<Tracking[]>('/orders/' + o.id + '/tracking')).slice().reverse()
  } catch (e) {
    showFailToast((e as Error).message)
  } finally {
    trackingLoading.value = false
  }
}
async function move(delta: number) {
  page.value += delta
  await load()
}
onMounted(() => {
  void load()
  timer = setInterval(() => {
    if (
      !busy.value &&
      !loading.value &&
      orders.value.some(
        (o) => o.status === 'PENDING_PAYMENT' || cases.value[o.id]?.status === 'REFUNDING',
      )
    )
      void load(true)
  }, 5000)
})
onUnmounted(() => {
  disposed = true
  if (timer) clearInterval(timer)
})
</script>
<template>
  <section>
    <div class="section-title">
      <div>
        <h1>我的订单</h1>
        <p>从下单到送达，每一步都看得见。</p>
      </div>
      <van-button size="small" plain :loading="loading" @click="load()">刷新</van-button>
    </div>
    <van-tabs v-model:active="active" class="order-tabs" color="var(--tt-primary-active)">
      <van-tab name="all" title="全部" />
      <van-tab name="PENDING_PAYMENT" title="待付款" />
      <van-tab name="AWAITING_SHIPMENT" title="待发货" />
      <van-tab name="SHIPPED" title="待收货" />
    </van-tabs>
    <div v-if="error" role="alert" class="error-panel">
      <p>{{ error }}</p>
      <van-button size="small" @click="load()">重试</van-button>
    </div>
    <van-skeleton v-if="loading" title :row="6" />
    <van-empty v-else-if="!visible.length" description="暂无符合条件的订单">
      <van-button type="primary" to="/products">看看店铺好物</van-button>
    </van-empty>
    <template v-else>
      <article v-for="o in visible" :key="o.id" class="order-card">
        <div class="row order-header">
          <strong>订单 #{{ o.id }}</strong>
          <van-tag :type="color(o.status)" plain>{{ label(o.status) }}</van-tag>
        </div>
        <div v-for="i in o.items" :key="i.productId" class="order-item">
          <div>
            <strong>{{ i.productName }}</strong>
            <br />
            <small>{{ money(i.unitPriceCents) }} × {{ i.quantity }}</small>
          </div>
          <strong>{{ money(i.unitPriceCents * i.quantity) }}</strong>
        </div>
        <p class="order-address">
          <van-icon name="location-o" />
          {{ o.address }}
        </p>
        <div class="order-total">
          合计
          <strong class="product-price">{{ money(o.totalCents) }}</strong>
        </div>
        <van-notice-bar
          v-if="o.fulfillmentHold"
          text="售后处理中，订单履约已暂停。"
          left-icon="info-o"
        />
        <div v-if="cases[o.id]" class="after-sale-box">
          <div class="row">
            <strong>售后进度</strong>
            <van-tag plain :type="cases[o.id].status === 'REFUNDED' ? 'success' : 'primary'">
              {{ label(cases[o.id].status) }}
            </van-tag>
          </div>
          <p>{{ cases[o.id].decisionReason || '商家将尽快处理你的申请。' }}</p>
          <p v-if="details[o.id]?.refund.status">
            退款：{{ label(details[o.id].refund.status!) }} ·
            {{ money(details[o.id].refund.amountCents || 0) }}
          </p>
          <van-button
            v-if="cases[o.id].status === 'WAITING_RETURN'"
            size="small"
            plain
            :disabled="busy"
            @click="open(o, 'return')"
          >
            填写退货运单
          </van-button>
        </div>
        <div class="order-actions">
          <template v-if="o.status === 'PENDING_PAYMENT'">
            <van-button size="small" plain :disabled="busy" @click="cancel(o)">取消订单</van-button>
            <van-button size="small" type="primary" :disabled="busy" @click="pay(o)">
              立即付款
            </van-button>
          </template>
          <van-button v-if="o.trackingNo" size="small" plain :disabled="busy" @click="track(o)">
            查看物流
          </van-button>
          <van-button
            v-if="['AWAITING_SHIPMENT', 'SHIPPED', 'COMPLETED'].includes(o.status) && !cases[o.id]"
            size="small"
            plain
            :disabled="busy"
            @click="open(o, 'apply')"
          >
            申请售后
          </van-button>
          <van-button
            v-if="o.status === 'SHIPPED' && !o.fulfillmentHold"
            size="small"
            type="primary"
            :disabled="busy"
            @click="confirm(o)"
          >
            确认收货
          </van-button>
        </div>
      </article>
    </template>
    <div class="row">
      <van-button size="small" plain :disabled="loading || page === 0" @click="move(-1)">
        上一页
      </van-button>
      <span class="muted">第 {{ page + 1 }} 页</span>
      <van-button size="small" plain :disabled="loading || orders.length < 20" @click="move(1)">
        下一页
      </van-button>
    </div>
    <van-popup
      v-model:show="popup"
      position="bottom"
      round
      closeable
      :style="{ maxHeight: '80vh' }"
    >
      <section class="popup-content">
        <h2>
          {{
            popupMode === 'apply'
              ? '申请售后'
              : popupMode === 'return'
                ? '填写退货运单'
                : '物流轨迹'
          }}
        </h2>
        <p class="muted">
          订单 #{{ selected?.id }}
          <span v-if="popupMode === 'tracking'">· {{ selected?.trackingNo }}</span>
        </p>
        <template v-if="popupMode === 'tracking'">
          <van-loading v-if="trackingLoading" />
          <van-empty v-else-if="!timeline.length" description="包裹已发出，等待物流更新" />
          <ol v-else class="timeline">
            <li v-for="t in timeline" :key="t.eventId">
              <strong>{{ label(t.status) }}</strong>
              <p>{{ t.location }}</p>
              <small>{{ localTime(t.occurredAt) }}</small>
            </li>
          </ol>
        </template>
        <template v-else>
          <van-field
            v-model="input"
            :type="popupMode === 'apply' ? 'textarea' : 'text'"
            :label="popupMode === 'apply' ? '申请原因' : '退货运单'"
            :placeholder="popupMode === 'apply' ? '请描述需要售后的原因' : '请输入寄回包裹的运单号'"
            :maxlength="popupMode === 'apply' ? 500 : 100"
          />
          <van-button type="primary" block :loading="busy" class="form-submit" @click="submitPopup">
            提交
          </van-button>
        </template>
      </section>
    </van-popup>
  </section>
</template>
