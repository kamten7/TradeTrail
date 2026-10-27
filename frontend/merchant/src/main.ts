import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ui from './ui'
import './tokens.css'
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/products' },
    { path: '/products', component: () => import('./pages/ProductsPage.vue') },
    { path: '/orders', component: () => import('./pages/OrdersPage.vue') },
    { path: '/after-sales', component: () => import('./pages/AfterSalesPage.vue') },
    { path: '/recovery', component: () => import('./pages/RecoveryPage.vue') },
  ],
})
createApp(App).use(createPinia()).use(router).use(ui).mount('#app')
