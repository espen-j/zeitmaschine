import Vue from 'vue';
import router from './router';
import App from './App.vue';
import {authService} from "./auth/auth-service";

Vue.config.productionTip = false;

authService.reset();

new Vue({
    router,
    render: h => h(App)
}).$mount('#app');
