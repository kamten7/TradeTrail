// 购物车界面状态以服务端报价为准，不在客户端锁定成交价。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type CartItem, type Quote } from '../api'
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  const quotes = ref<Quote[]>([])
  async function refresh() {
    items.value = await api('/cart/items')
    quotes.value = await api('/cart/quote')
  }
  return { items, quotes, refresh }
})
