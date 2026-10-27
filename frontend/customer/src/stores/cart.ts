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
