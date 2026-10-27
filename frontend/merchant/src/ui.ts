import type { App } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
  ElLoading,
} from 'element-plus'
import 'element-plus/dist/index.css'
export default {
  install(app: App) {
    for (const component of [
      ElAlert,
      ElButton,
      ElCard,
      ElCheckbox,
      ElDescriptions,
      ElDescriptionsItem,
      ElDialog,
      ElEmpty,
      ElForm,
      ElFormItem,
      ElInput,
      ElInputNumber,
      ElOption,
      ElPagination,
      ElSelect,
      ElSkeleton,
      ElSwitch,
      ElTable,
      ElTableColumn,
      ElTag,
      ElTooltip,
    ])
      app.use(component)
    app.use(ElLoading)
  },
}
