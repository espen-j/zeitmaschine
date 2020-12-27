import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import {store, key} from './store'
import {authService} from './auth/auth-service'

authService.reset();

createApp(App)
  .use(store, key)
  .use(router)
  .mount('#app')
