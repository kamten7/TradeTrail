<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, login, session, type Product, type Order } from './api'

const route = useRoute()
const router = useRouter()
const section = computed(() => route.path.startsWith('/orders') ? 'orders' : 'products')
const user = ref('')
const username = ref('merchant')
const password = ref('merchant123')
const products = ref<Product[]>([])
const orders = ref<Order[]>([])
const loading = ref(false)
const error = ref('')
const form = reactive({ name: '', description: '', priceCents: 1000, initialStock: 10 })
const money = (value: number) => `¥${(value / 100).toFixed(2)}`

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    user.value = await session()
    if (user.value) {
      products.value = await api<Product[]>('/admin/products')
      orders.value = await api<Order[]>('/admin/orders')
    }
  } catch (cause) { error.value = (cause as Error).message }
  finally { loading.value = false }
}
async function signIn() {
  if (!await login(username.value, password.value)) { error.value = '登录失败'; return }
  await refresh()
}
async function create() {
  try { await api('/admin/products', 'POST', form); ElMessage.success('商品已创建'); form.name = ''; await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
async function publish(id: number) {
  try { await api(`/admin/products/${id}/publish`, 'POST'); ElMessage.success('已上架'); await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
async function adjust(id: number) {
  try {
    const deltaInput = await ElMessageBox.prompt('正数入库，负数扣减', '调整库存', { inputPattern: /^-?[1-9]\d*$/, inputErrorMessage: '请输入非零整数' })
    const reasonInput = await ElMessageBox.prompt('填写调整原因', '调整库存', { inputPattern: /\S+/, inputErrorMessage: '原因不能为空' })
    await api(`/admin/products/${id}/stock-adjustments`, 'POST', { delta: Number(deltaInput.value), reason: reasonInput.value })
    ElMessage.success('库存已调整')
    await refresh()
  } catch (cause) { if (cause !== 'cancel' && cause !== 'close') error.value = (cause as Error).message }
}
async function ship(id: number) {
  try { await api(`/admin/orders/${id}/shipments`, 'POST'); ElMessage.success('发货成功'); await refresh() }
  catch (cause) { error.value = (cause as Error).message }
}
onMounted(refresh)
</script>

<template>
  <div class="shell">
    <aside class="sidebar"><h2>TradeTrail</h2><p>店家管理台</p><el-menu :default-active="section" @select="(key: string) => router.push('/' + key)"><el-menu-item index="products">商品管理</el-menu-item><el-menu-item index="orders">订单履约</el-menu-item></el-menu></aside>
    <div class="body"><header class="topbar"><strong>{{ section === 'orders' ? '订单履约' : '商品管理' }}</strong><span>{{ user || '未登录' }}</span></header>
      <main>
        <el-card v-if="!user" class="login"><h2>店家登录</h2><el-form label-width="100px"><el-form-item label="账号"><el-input v-model="username" /></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" /></el-form-item><el-button type="primary" @click="signIn">登录</el-button></el-form></el-card>
        <el-alert v-if="error" :title="error" type="error" show-icon class="alert" />
        <div v-if="user" v-loading="loading">
          <template v-if="section === 'products'"><el-card class="panel"><template #header><h1>新建商品</h1></template><el-form label-width="100px" class="form"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" /></el-form-item><el-form-item label="价格（分）"><el-input-number v-model="form.priceCents" :min="1" /></el-form-item><el-form-item label="初始库存"><el-input-number v-model="form.initialStock" :min="0" /></el-form-item><el-button type="primary" @click="create">创建商品</el-button></el-form></el-card>
            <el-card class="panel"><template #header><h1>商品列表</h1></template><el-empty v-if="products.length === 0" description="暂无商品" /><el-table v-else :data="products" stripe><el-table-column prop="id" label="ID" width="70" /><el-table-column prop="name" label="商品" /><el-table-column label="售价" width="120" align="right"><template #default="scope">{{ money(scope.row.priceCents) }}</template></el-table-column><el-table-column prop="available" label="可售" width="90" align="right" /><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.published ? 'success' : 'info'">{{ scope.row.published ? '已上架' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button v-if="!scope.row.published" type="primary" link @click="publish(scope.row.id)">上架</el-button><el-button link @click="adjust(scope.row.id)">调整库存</el-button></template></el-table-column></el-table></el-card>
          </template>
          <el-card v-else class="panel"><template #header><h1>订单列表</h1></template><el-empty v-if="orders.length === 0" description="暂无订单" /><el-table v-else :data="orders" stripe><el-table-column prop="id" label="订单号" width="100" /><el-table-column prop="customer" label="客户" /><el-table-column prop="productId" label="商品 ID" width="100" /><el-table-column label="金额" width="120" align="right"><template #default="scope">{{ money(scope.row.totalCents) }}</template></el-table-column><el-table-column label="状态" width="150"><template #default="scope"><el-tag>{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column prop="trackingNo" label="运单号" /><el-table-column label="操作" width="120" fixed="right"><template #default="scope"><el-button v-if="scope.row.status === 'PAID'" type="primary" link @click="ship(scope.row.id)">发货</el-button></template></el-table-column></el-table></el-card>
        </div>
      </main>
    </div>
  </div>
</template>
