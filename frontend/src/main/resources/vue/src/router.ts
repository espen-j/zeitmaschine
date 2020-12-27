import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import Gallery from './components/Gallery.vue'
import Callback from './components/Callback.vue'
import { authService } from './auth/auth-service'
import Slider from './components/Slider.vue'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'home',
    component: Gallery,
    meta: { authRequired: true }

  },
  {
    path: '/callback',
    name: 'callback',
    component: Callback
  },
  {
    path: '/slide',
    name: 'slide',
    component: Slider,
    meta: { authRequired: true }
  }
]

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.authRequired) {
    if (authService.isAuthenticated()) {
      next()
    } else {
      authService.login()
    }
  } else {
    next()
  }
})

export default router
