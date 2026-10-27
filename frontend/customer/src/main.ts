import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ui from './ui'
import { useSessionStore } from './stores/session'
import './tokens.css'
import './style.css'
const pinia = createPinia()
const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/products', component: () => import('./pages/ProductsPage.vue') },
    {
      path: '/cart',
      component: () => import('./pages/CartPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/orders',
      component: () => import('./pages/OrdersPage.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/account', component: () => import('./pages/AccountPage.vue') },
    { path: '/login', component: () => import('./pages/LoginPage.vue') },
  ],
})
router.beforeEach(async (to) => {
  const auth = useSessionStore(pinia)
  if (!auth.checked) await auth.refresh()
  if (to.meta.requiresAuth && !auth.user)
    return { path: '/login', query: { redirect: to.fullPath } }
})
createApp(App).use(pinia).use(router).use(ui).mount('#app')
