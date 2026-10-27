<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { useSessionStore } from './stores/session'
import AppIcon from './components/AppIcon.vue'
const auth = useSessionStore()
const route = useRoute()
const form = ref({ username: 'merchant', password: '' })
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loginError = ref('')
const rules = {
  username: [{ required: true, message: '请输入店家账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const navigation = [
  { path: '/products', name: '商品与库存', icon: 'products' as const, sub: '管理商品' },
  { path: '/orders', name: '订单履约', icon: 'orders' as const, sub: '处理订单' },
  { path: '/after-sales', name: '售后管理', icon: 'afterSale' as const, sub: '服务客户' },
  { path: '/recovery', name: '异常恢复', icon: 'recovery' as const, sub: '处理异常' },
]
const current = computed(
  () => navigation.find((item) => route.path.startsWith(item.path)) || navigation[0],
)
async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  loginError.value = ''
  try {
    await auth.signIn(form.value.username, form.value.password)
    form.value.password = ''
  } catch (error) {
    loginError.value = (error as Error).message
  } finally {
    submitting.value = false
  }
}
async function signOut() {
  try {
    await auth.signOut()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
function expired() {
  auth.user = ''
  loginError.value = '登录已过期，请重新登录。'
}
onMounted(() => {
  void auth.refresh()
  window.addEventListener('tradetrail:unauthenticated', expired)
})
onUnmounted(() => window.removeEventListener('tradetrail:unauthenticated', expired))
</script>
<template>
  <div v-if="auth.checking" class="boot-screen">
    <span class="brand-mark">T</span>
    <el-skeleton :rows="2" animated />
    <p>正在连接店铺…</p>
  </div>
  <div v-else-if="!auth.user" class="login-screen">
    <div class="login-brand">
      <span class="brand-mark">T</span>
      <strong>
        TradeTrail
        <span>商迹</span>
      </strong>
      <span class="login-edition">店家工作台</span>
    </div>
    <main class="login-layout">
      <section class="login-story">
        <span class="eyebrow">YOUR STORE. EVERY STEP.</span>
        <h1>
          每一笔生意，
          <br />
          都有迹可循
          <span class="accent">。</span>
        </h1>
        <p>
          从商品上架到订单送达，
          <br />
          让店铺的日常经营，清晰有序。
        </p>
        <div class="journey-preview" aria-hidden="true">
          <div class="preview-top">
            <span class="preview-dot"></span>
            <span>订单履约旅程</span>
            <span class="preview-badge">全程可追踪</span>
          </div>
          <div class="journey-step">
            <span class="step-icon"><AppIcon name="products" /></span>
            <div>
              <strong>准备好商品</strong>
              <small>商品、售价与库存，同步管理</small>
            </div>
            <AppIcon name="check" />
          </div>
          <div class="journey-step">
            <span class="step-icon"><AppIcon name="orders" /></span>
            <div>
              <strong>处理每一份订单</strong>
              <small>支付确认后，有序安排发货</small>
            </div>
            <AppIcon name="check" />
          </div>
          <div class="journey-step">
            <span class="step-icon"><AppIcon name="afterSale" /></span>
            <div>
              <strong>照顾好每一位客户</strong>
              <small>物流进度与售后处理，一目了然</small>
            </div>
            <AppIcon name="check" />
          </div>
        </div>
      </section>
      <section class="login-card">
        <div class="login-card-icon"><AppIcon name="store" /></div>
        <h2>欢迎回到商迹</h2>
        <p class="muted">登录店家账号，开始管理你的店铺。</p>
        <el-alert
          v-if="loginError || auth.error"
          :title="loginError || auth.error"
          type="error"
          :closable="false"
          show-icon
          class="login-alert"
        />
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item label="店家账号" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              placeholder="请输入店家账号"
            />
          </el-form-item>
          <el-form-item label="登录密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入登录密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="submitting" class="login-submit">
            登录工作台
            <AppIcon name="arrow" />
          </el-button>
        </el-form>
        <div class="login-tip">
          <AppIcon name="store" />
          <span>店家专属入口 · 管理商品、订单与售后</span>
        </div>
        <el-button v-if="auth.error" text @click="auth.refresh">重新检查连接</el-button>
      </section>
    </main>
    <footer class="login-footer">
      <span>TradeTrail · 单店官方商城</span>
      <span>让交易有序，让服务可追踪</span>
    </footer>
  </div>
  <div v-else class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink to="/products" class="sidebar-brand">
        <span class="brand-mark">T</span>
        <div>
          <strong>TradeTrail</strong>
          <small>商迹 · 店家工作台</small>
        </div>
      </RouterLink>
      <div class="store-chip">
        <AppIcon name="store" />
        <div>
          <strong>官方店铺</strong>
          <small>单店经营管理</small>
        </div>
        <span class="online-dot"></span>
      </div>
      <div class="nav-caption">店铺管理</div>
      <nav>
        <RouterLink
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          class="sidebar-link"
          :class="{ active: current.path === item.path }"
        >
          <AppIcon :name="item.icon" />
          <span>{{ item.name }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <span class="brand-initial">{{ auth.user.slice(0, 1).toUpperCase() }}</span>
        <div>
          <strong>{{ auth.user }}</strong>
          <small>店家管理员</small>
        </div>
        <el-button text aria-label="退出登录" @click="signOut"><AppIcon name="logout" /></el-button>
      </div>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <div>
          <span class="muted">工作台</span>
          <span class="breadcrumb-divider">/</span>
          <strong>{{ current.name }}</strong>
        </div>
        <span class="header-store">
          <span class="online-dot"></span>
          官方店铺
        </span>
      </header>
      <main class="admin-content"><RouterView :key="auth.revision" /></main>
      <footer class="admin-footer">TradeTrail 商迹 · 每一步，都有记录</footer>
    </div>
  </div>
</template>
