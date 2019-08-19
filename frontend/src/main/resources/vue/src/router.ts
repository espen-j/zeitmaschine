import Vue from 'vue';
import Router from 'vue-router';
import Gallery from './components/Gallery.vue';
import Callback from './components/Callback.vue';
import {authService} from './auth/auth-service';

Vue.use(Router);

const router = new Router({
  mode: 'history',
  routes: [
    {
      path: '/',
      name: 'home',
      component: Gallery,
      meta: {authRequired: true}

    },
    {
      path: '/callback',
      name: 'callback',
      component: Callback
    }
  ]
});

router.beforeEach((to, from, next) => {
  if (to.meta.authRequired) {
    if (authService.isAuthenticated()) {
      next();
    } else {
      authService.login();
    }
  } else {
    next();
  }
});

export default router;
