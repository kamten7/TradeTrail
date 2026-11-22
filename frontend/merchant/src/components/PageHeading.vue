<script setup lang="ts">
// 复用管理台页面标题与操作区布局。
defineProps<{ title: string; description: string }>()
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <div class="heading-actions"><slot /></div>
  </div>
</template>
