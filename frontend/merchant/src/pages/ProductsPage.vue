<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { api, money, type Product } from '../api'
import AppIcon from '../components/AppIcon.vue'
import PageHeading from '../components/PageHeading.vue'
import LoadError from '../components/LoadError.vue'
const products = ref<Product[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const search = ref('')
const status = ref('')
const page = ref(1)
const editing = ref<number | null>(null)
const visible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
  priceCents: 1000,
  initialStock: 0,
  published: false,
})
const stockVisible = ref(false)
const stockProduct = ref<Product>()
const adjustment = reactive({ delta: 1, reason: '' })
const filtered = computed(() =>
  products.value.filter(
    (p) =>
      (!search.value || (p.name + p.id).includes(search.value)) &&
      (!status.value || p.published === (status.value === 'published')),
  ),
)
const rows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
const published = computed(() => products.value.filter((p) => p.published).length)
const lowStock = computed(() => products.value.filter((p) => p.available <= 5).length)
const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  priceCents: [
    {
      type: 'number' as const,
      required: true,
      min: 1,
      max: 100000000,
      message: '价格须在 1 至 100000000 分之间',
      trigger: 'blur',
    },
  ],
  initialStock: [{ type: 'number' as const, min: 0, message: '库存不能为负', trigger: 'blur' }],
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    products.value = await api('/admin/products')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
function open(product?: Product) {
  editing.value = product?.id || null
  Object.assign(
    form,
    product
      ? { ...product, initialStock: 0 }
      : { name: '', description: '', priceCents: 1000, initialStock: 0, published: false },
  )
  visible.value = true
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    if (editing.value)
      await api('/admin/products/' + editing.value, 'PUT', {
        name: form.name,
        description: form.description,
        priceCents: form.priceCents,
        published: form.published,
      })
    else
      await api('/admin/products', 'POST', {
        name: form.name,
        description: form.description,
        priceCents: form.priceCents,
        initialStock: form.initialStock,
      })
    visible.value = false
    ElMessage.success(editing.value ? '商品已更新' : '商品已创建，可在列表中上架')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
async function toggle(product: Product) {
  try {
    await ElMessageBox.confirm(
      product.published ? '下架后用户将无法购买，历史订单不受影响。' : '确认将该商品上架销售？',
      product.published ? '下架商品' : '上架商品',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await api('/admin/products/' + product.id, 'PUT', {
      name: product.name,
      description: product.description,
      priceCents: product.priceCents,
      published: !product.published,
    })
    ElMessage.success('商品状态已更新')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
function openStock(product: Product) {
  stockProduct.value = product
  adjustment.delta = 1
  adjustment.reason = ''
  stockVisible.value = true
}
async function adjust() {
  if (!stockProduct.value || saving.value) return
  if (!Number.isInteger(adjustment.delta) || adjustment.delta === 0 || !adjustment.reason.trim()) {
    ElMessage.warning('请填写非零调整数量和原因')
    return
  }
  saving.value = true
  try {
    await api('/admin/products/' + stockProduct.value.id + '/stock-adjustments', 'POST', adjustment)
    stockVisible.value = false
    ElMessage.success('库存已调整并记录流水')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<template>
  <section>
    <PageHeading title="商品与库存" description="管理店铺商品，让每一份库存都有记录。">
      <el-button type="primary" @click="open()">＋ 新建商品</el-button>
    </PageHeading>
    <div class="metrics">
      <div class="metric metric-accent">
        <div class="metric-label">全部商品</div>
        <div class="metric-value">{{ products.length }}</div>
        <div class="metric-note">店铺商品总数</div>
      </div>
      <div class="metric">
        <div class="metric-label">正在销售</div>
        <div class="metric-value">{{ published }}</div>
        <div class="metric-note">已上架商品</div>
      </div>
      <div class="metric">
        <div class="metric-label">未上架</div>
        <div class="metric-value">{{ products.length - published }}</div>
        <div class="metric-note">草稿与已下架商品</div>
      </div>
      <div class="metric">
        <div class="metric-label">库存关注</div>
        <div class="metric-value">{{ lowStock }}</div>
        <div class="metric-note">可售库存不超过 5 件</div>
      </div>
    </div>
    <LoadError :message="error" @retry="load" />
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-input
          v-model="search"
          aria-label="搜索商品"
          placeholder="搜索商品名称 / 编号"
          clearable
          @input="page = 1"
        />
        <el-select
          v-model="status"
          aria-label="商品状态"
          placeholder="全部状态"
          clearable
          @change="page = 1"
        >
          <el-option label="已上架" value="published" />
          <el-option label="未上架" value="draft" />
        </el-select>
        <el-button :loading="loading" @click="load"><AppIcon name="refresh" /></el-button>
        <span class="toolbar-count">共 {{ filtered.length }} 件商品</span>
      </div>
      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="商品信息" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <span class="product-symbol"><AppIcon name="products" /></span>
              <div>
                <strong>{{ row.name }}</strong>
                <div class="cell-sub">商品编号 #{{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="销售状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'" effect="light">
              {{ row.published ? '已上架' : '未上架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="售价" width="140" align="right">
          <template #default="{ row }">
            <span class="price">{{ money(row.priceCents) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可售库存" width="140" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.available <= 5" type="warning" size="small">
              {{ row.available }} 件
            </el-tag>
            <span v-else>{{ row.available }} 件</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="228" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="saving" @click="open(row)">编辑</el-button>
            <el-button link type="primary" :disabled="saving" @click="toggle(row)">
              {{ row.published ? '下架' : '上架' }}
            </el-button>
            <el-button link :disabled="saving" @click="openStock(row)">调整库存</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有符合条件的商品">
            <el-button type="primary" @click="open()">新建商品</el-button>
          </el-empty>
        </template>
      </el-table>
      <div class="table-pagination">
        <span>价格与库存以当前记录为准</span>
        <el-pagination
          v-model:current-page="page"
          :page-size="10"
          :total="filtered.length"
          layout="prev, pager, next"
        />
      </div>
    </el-card>
    <el-dialog
      v-model="visible"
      :title="editing ? '编辑商品' : '新建商品'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p class="dialog-note">商品改价不会影响已创建订单的成交价格。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="120" placeholder="输入商品名称" />
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="售价（分）" prop="priceCents">
          <el-input-number v-model="form.priceCents" :min="1" :max="100000000" :precision="0" />
          <span class="cell-sub">&nbsp;{{ money(form.priceCents) }}</span>
        </el-form-item>
        <el-form-item v-if="!editing" label="初始库存" prop="initialStock">
          <el-input-number v-model="form.initialStock" :min="0" :precision="0" />
        </el-form-item>
        <el-form-item v-if="editing" label="销售状态">
          <el-switch v-model="form.published" active-text="上架销售" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存商品</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="stockVisible" title="调整库存" width="480px" :close-on-click-modal="false">
      <p class="dialog-note">
        {{ stockProduct?.name }} · 当前可售 {{ stockProduct?.available }} 件
      </p>
      <el-form label-width="100px">
        <el-form-item label="调整数量">
          <el-input-number v-model="adjustment.delta" :precision="0" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input
            v-model="adjustment.reason"
            type="textarea"
            maxlength="300"
            placeholder="如补货入库、盘点修正"
          />
        </el-form-item>
      </el-form>
      <p class="dialog-note">正数增加可售库存，负数扣减；每次调整都会保留操作记录。</p>
      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="adjust">确认调整</el-button>
      </template>
    </el-dialog>
  </section>
</template>
