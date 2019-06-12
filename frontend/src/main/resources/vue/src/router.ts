import Vue from 'vue';
import Router from 'vue-router';
import Gallery from './components/Gallery.vue';
import Callback from './components/Callback.vue';

Vue.use(Router);

const router = new Router({
  mode: 'history',
  routes: [
    {
      path: '/',
      name: 'home',
      component: Gallery

    },
    {
      path: '/callback',
      name: 'callback',
      component: Callback
    }
  ]
});

export default router;
