import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './tokens.css'
import './style.css'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/products' },
  { path: '/products', component: App },
  { path: '/cart', component: App },
  { path: '/orders', component: App }
] })
createApp(App).use(createPinia()).use(router).mount('#app')
