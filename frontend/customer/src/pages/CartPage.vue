<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showConfirmDialog } from 'vant'
import { api, money, type CartItem, type Product } from '../api'
import { useCartStore } from '../stores/cart'
const cart = useCartStore()
const router = useRouter()
const products = ref<Product[]>([])
const address = ref('')
const error = ref('')
const busy = ref(false)
const loading = ref(true)
const total = computed(() => cart.quotes.reduce((sum, q) => sum + q.subtotalCents, 0))
const name = (id: number) =>
  cart.quotes.find((p) => p.productId === id)?.productName ||
  products.value.find((p) => p.id === id)?.name ||
  '商品 #' + id
async function load() {
  loading.value = true
  error.value = ''
  try {
    await cart.refresh()
    products.value = await api('/products')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function update(item: CartItem) {
  if (busy.value) return
  busy.value = true
  try {
    await api('/cart/items/' + item.id, 'PUT', { quantity: item.quantity, selected: item.selected })
    await cart.refresh()
  } catch (e) {
    showFailToast((e as Error).message)
    await load()
  } finally {
    busy.value = false
  }
}
async function remove(item: CartItem) {
  try {
    await showConfirmDialog({ title: '移除商品', message: '确定将这件商品移出购物车？' })
  } catch {
    return
  }
  busy.value = true
  try {
    await api('/cart/items/' + item.id, 'DELETE')
    await load()
  } catch (e) {
    showFailToast((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function checkout() {
  if (busy.value) return
  if (!address.value.trim()) {
    showFailToast('请填写完整收货信息')
    return
  }
  busy.value = true
  error.value = ''
  try {
    await api('/cart/checkout', 'POST', {
      address: address.value,
      items: cart.quotes.map((q) => ({
        productId: q.productId,
        quantity: q.quantity,
        expectedPriceCents: q.unitPriceCents,
      })),
    })
    await router.push('/orders')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <section>
    <div class="section-title">
      <div>
        <h1>我的购物车</h1>
        <p>选好心意，准备带回家。</p>
      </div>
      <van-button size="small" plain @click="load" :loading="loading">刷新</van-button>
    </div>
    <div v-if="error" class="error-panel" role="alert">
      <p>{{ error }}</p>
      <van-button size="small" @click="load">重试</van-button>
    </div>
    <van-skeleton v-if="loading" title :row="5" />
    <van-empty v-else-if="!cart.items.length" description="购物车还是空的">
      <van-button type="primary" to="/products">去逛逛好物</van-button>
    </van-empty>
    <div v-else class="cart-layout">
      <div>
        <article v-for="item in cart.items" :key="item.id" class="cart-item">
          <van-checkbox
            v-model="item.selected"
            :aria-label="'选择 ' + name(item.productId)"
            :disabled="busy"
            @change="update(item)"
          />
          <div class="cart-item-art"><van-icon name="goods-collect-o" size="28" /></div>
          <div class="cart-item-main">
            <h3>{{ name(item.productId) }}</h3>
            <div class="row">
              <span class="muted">数量</span>
              <van-stepper
                v-model="item.quantity"
                :min="1"
                :max="10000"
                :disabled="busy"
                @change="update(item)"
              />
            </div>
            <div class="row cart-item-actions">
              <small class="muted">{{ item.selected ? '已选择' : '未选择' }}</small>
              <van-button size="small" plain :disabled="busy" @click="remove(item)">
                移除
              </van-button>
            </div>
          </div>
        </article>
      </div>
      <section class="checkout-card">
        <h2>确认结算</h2>
        <div v-for="q in cart.quotes" :key="q.productId" class="checkout-line">
          <div class="row">
            <span>{{ q.productName }} × {{ q.quantity }}</span>
            <strong>{{ money(q.subtotalCents) }}</strong>
          </div>
          <van-tag v-if="!q.available" type="warning">当前库存不足或已下架</van-tag>
        </div>
        <label class="checkout-address" for="address">收货信息</label>
        <van-field
          id="address"
          v-model="address"
          type="textarea"
          maxlength="500"
          rows="3"
          autosize
          placeholder="请填写收货人、联系电话和详细地址"
        />
        <div class="checkout-total row">
          <span>应付合计</span>
          <strong class="product-price">{{ money(total) }}</strong>
        </div>
        <van-button
          type="primary"
          block
          :loading="busy"
          :disabled="!cart.quotes.length || cart.quotes.some((q) => !q.available)"
          @click="checkout"
        >
          确认下单
        </van-button>
        <p class="cell-sub muted">下单前会再次核对价格与库存。</p>
      </section>
    </div>
  </section>
</template>
