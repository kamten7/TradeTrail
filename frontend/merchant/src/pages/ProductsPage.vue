<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, money, type Product } from '../api'
const products = ref<Product[]>([])
const busy = ref(false)
const error = ref('')
const form = reactive({ name: '', description: '', priceCents: 1000, initialStock: 10 })
async function load() { products.value = await api('/admin/products') }
async function action(task: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; try { await task(); await load() } catch (e) { error.value = (e as Error).message } finally { busy.value = false } }
async function create() { await action(async () => { await api('/admin/products', 'POST', form); form.name = ''; form.description = '' }) }
async function change(p: Product, published: boolean) { await action(async () => { await api('/admin/products/' + p.id, 'PUT', { name: p.name, description: p.description, priceCents: p.priceCents, published }) }) }
async function adjust(p: Product) { const delta = Number(prompt('调整数量：正数入库，负数扣减')); if (!Number.isInteger(delta) || !delta) return; const reason = prompt('请输入库存调整原因'); if (!reason) return; await action(async () => { await api('/admin/products/' + p.id + '/stock-adjustments', 'POST', { delta, reason }) }) }
onMounted(() => action(async () => {}))
</script>
<template><section><p v-if="error" role="alert" class="error">{{ error }}</p><form class="panel" @submit.prevent="create"><h2>新建商品</h2><label>名称<input v-model="form.name" maxlength="120" required></label><label>描述<textarea v-model="form.description" maxlength="1000"></textarea></label><label>价格（分）<input v-model.number="form.priceCents" type="number" min="1" max="100000000" required></label><label>初始库存<input v-model.number="form.initialStock" type="number" min="0" required></label><button :disabled="busy">创建</button></form>
<div class="panel table-scroll"><h2>商品维护</h2><table><thead><tr><th>名称</th><th>价格（分）</th><th>可售</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="p in products" :key="p.id"><td><input v-model="p.name" :aria-label="'商品名称 ' + p.id"></td><td><input v-model.number="p.priceCents" type="number" min="1" :aria-label="'商品价格 ' + p.id"><small>{{ money(p.priceCents) }}</small></td><td>{{ p.available }}</td><td>{{ p.published ? '在售' : '未上架' }}</td><td><button :disabled="busy" @click="change(p, p.published)">保存</button><button :disabled="busy" @click="change(p, !p.published)">{{ p.published ? '下架' : '上架' }}</button><button :disabled="busy" @click="adjust(p)">调整库存</button></td></tr></tbody></table></div></section></template>
