<script setup lang="ts">
defineProps<{ message: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <el-alert v-if="message" class="load-error" type="error" :closable="false" show-icon>
    <template #title>{{ message }}</template>
    <el-button size="small" @click="$emit('retry')">重新加载</el-button>
  </el-alert>
</template>
