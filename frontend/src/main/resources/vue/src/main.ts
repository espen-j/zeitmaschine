import Vue from 'vue';
import router from './router';
import App from './App.vue';
import store from './store'
import {authService} from './auth/auth-service';

Vue.config.productionTip = false;

authService.reset();

new Vue({
    router,
    store,
    render: h => h(App)
}).$mount('#app');
