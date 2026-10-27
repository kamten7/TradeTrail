import type { App } from 'vue'
import {
  Button,
  Cell,
  CellGroup,
  Checkbox,
  Dialog,
  Empty,
  Field,
  Form,
  Icon,
  Loading,
  NavBar,
  NoticeBar,
  Popup,
  Search,
  Skeleton,
  Stepper,
  Tab,
  Tabbar,
  TabbarItem,
  Tabs,
  Tag,
} from 'vant'
import 'vant/lib/index.css'
export default {
  install(app: App) {
    for (const component of [
      Button,
      Cell,
      CellGroup,
      Checkbox,
      Dialog,
      Empty,
      Field,
      Form,
      Icon,
      Loading,
      NavBar,
      NoticeBar,
      Popup,
      Search,
      Skeleton,
      Stepper,
      Tab,
      Tabbar,
      TabbarItem,
      Tabs,
      Tag,
    ])
      app.use(component)
  },
}
