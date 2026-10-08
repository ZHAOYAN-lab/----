import Vue from 'vue';
import ViewUI from 'view-design';
import 'view-design/dist/styles/iview.css';
import '@/lib/less/index.less';
import Console from './WarehouseConsole.vue';
Vue.use(ViewUI);
Vue.config.productionTip = false;
new Vue({ render: h => h(Console) }).$mount('#app');
