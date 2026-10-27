<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { api, money, type Product } from '../api'
import { useSessionStore } from '../stores/session'
const auth = useSessionStore()
const router = useRouter()
const products = ref<Product[]>([])
const search = ref('')
const loading = ref(true)
const busy = ref<number>()
const error = ref('')
const filtered = computed(() =>
  products.value.filter((p) => (p.name + p.description).includes(search.value)),
)
async function load() {
  loading.value = true
  error.value = ''
  try {
    products.value = await api('/products')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function add(p: Product) {
  if (!auth.user) {
    await router.push({ path: '/login', query: { redirect: '/cart' } })
    return
  }
  if (busy.value) return
  busy.value = p.id
  try {
    await api('/cart/items', 'POST', { productId: p.id, quantity: 1 })
    showSuccessToast('已加入购物车')
  } catch (e) {
    showFailToast((e as Error).message)
  } finally {
    busy.value = undefined
  }
}
onMounted(load)
</script>
<template>
  <section>
    <div class="shop-hero">
      <div>
        <span class="eyebrow">精选好物 · 官方直售</span>
        <h1>
          认真做好物，
          <br />
          安心到你手。
        </h1>
        <p>从下单到收货，每一步都看得见。</p>
      </div>
      <div class="hero-parcel" aria-hidden="true">
        <van-icon name="goods-collect-o" size="56" />
      </div>
    </div>
    <div class="store-promises">
      <span>
        <van-icon name="shop-o" />
        官方店铺
      </span>
      <span>
        <van-icon name="logistics" />
        物流可追踪
      </span>
      <span>
        <van-icon name="service-o" />
        售后有保障
      </span>
    </div>
    <div class="section-title">
      <div>
        <h2>店铺好物</h2>
        <p>发现适合日常的好选择</p>
      </div>
      <span class="muted">{{ products.length }} 件商品</span>
    </div>
    <van-search v-model="search" placeholder="搜索商品" aria-label="搜索商品" shape="round" />
    <div v-if="error" class="error-panel" role="alert">
      <p>{{ error }}</p>
      <van-button size="small" @click="load">重试</van-button>
    </div>
    <van-skeleton v-if="loading" title :row="5" />
    <van-empty v-else-if="!filtered.length" description="暂时没有符合条件的商品" />
    <div v-else class="product-grid">
      <article v-for="p in filtered" :key="p.id" class="product-card">
        <div class="product-art" aria-hidden="true">
          <van-icon name="goods-collect-o" size="48" />
        </div>
        <div class="product-body">
          <h3>{{ p.name }}</h3>
          <p class="product-description">{{ p.description || '官方店铺直售，品质用心呈现。' }}</p>
          <div class="product-bottom">
            <span class="product-price">{{ money(p.priceCents) }}</span>
            <van-button
              type="primary"
              size="small"
              :loading="busy === p.id"
              :disabled="!!busy || p.available < 1"
              icon="plus"
              @click="add(p)"
            >
              加购
            </van-button>
          </div>
          <div class="product-stock">
            {{ p.available > 0 ? '现货 · 可售 ' + p.available + ' 件' : '暂时售罄' }}
          </div>
        </div>
      </article>
    </div>
    <p v-if="!loading && filtered.length" class="list-end">已经看到全部好物了</p>
  </section>
</template>
