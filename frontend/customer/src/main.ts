import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './tokens.css'
import './style.css'
const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/products' },
  { path: '/products', component: () => import('./pages/ProductsPage.vue') },
  { path: '/cart', component: () => import('./pages/CartPage.vue') },
  { path: '/orders', component: () => import('./pages/OrdersPage.vue') }
] })
createApp(App).use(createPinia()).use(router).mount('#app')
