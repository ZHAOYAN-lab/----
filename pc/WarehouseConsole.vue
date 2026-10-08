<template>
  <div class="sl-app-main" :class="{ mobile }" @click="dismissBubble">
    <div class="sl-app-main-content">
      <header class="main-header-bar">
        <div class="h-left"><span class="logo"></span><span class="name">{{ tr("仓库定位系统") }}</span></div>
        <div class="h-right"><select v-if="!mobile" class="language-select" :aria-label="lang === 'ja' ? '言語' : '语言'" v-model="lang" @change="saveLanguage"><option value="zh">中文</option><option value="ja">日本語</option></select><span class="date-time">{{ tr(connection) }}</span><Button type="text" @click="page = page === 'settings' ? 'map' : 'settings'">{{ tr(page === 'settings' ? '返回地图' : '系统设置') }}</Button></div>
      </header>
      <div class="sl-app-main-content-layout">
        <aside class="main-side-bar">
          <div class="side-bar-menu"><div class="menu-un-collapsed">
            <Menu :active-name="page" class="side-bar-menu-iview" width="auto" @on-select="page = $event">
              <MenuItem v-for="item in menus" :key="item.id" :name="item.id"><div class="menu-p"><span class="icon-menu" :class="item.icon"></span><span class="title-span">{{ tr(item.name) }}</span></div></MenuItem>
            </Menu>
          </div></div>
        </aside>
        <main class="main">
          <Alert v-if="error" type="error" show-icon closable @on-close="error = ''">{{ tr(error) }}</Alert>
          <div v-if="page === 'map'" class="map-layout">
            <Card class="sl-card sl-card-01 map-card" :bordered="false" dis-hover>
              <div slot="title" class="title"><span class="icon icon-1"></span><span>{{ tr("首页") }}</span></div>
              <div slot="extra" class="extra">
                <label v-if="!mobile">{{ tr("位置编辑") }} <i-switch v-model="editing" /></label>
                <Select v-model="warehouseId" :placeholder="tr('选择仓库')" class="select-wh" @on-change="selectWarehouse"><Option v-for="w in state.warehouses" :key="w.id" :value="w.id">{{ w.name }}</Option></Select>
                <Select v-model="mapId" :placeholder="tr('选择地图')" class="select-map"><Option v-for="m in warehouseMaps" :key="m.id" :value="m.id">{{ m.name }} · {{ m.floor }}</Option></Select>
              </div>
              <div class="map-toolbar">
                <Input v-model="query" search clearable @on-search="searchLocation" @on-clear="resetView" :placeholder="tr('搜索商品名、SKU、信标码、货架')" />
                <Button @click="resetView">{{ tr("全图") }}</Button><Button @click="setZoom(zoom * 1.25)">＋</Button><Button @click="setZoom(zoom / 1.25)">－</Button>
                <Button v-if="!mobile" type="primary" @click="openUpload">{{ tr("上传地图") }}</Button>
              </div>
              <Alert v-if="editing" show-icon>{{ tr("选择右侧商品／基站，在图上点击放置；拖动标记调整位置。") }}<strong>{{ selectedLabel }}</strong></Alert>
              <div ref="viewport" class="map-viewport" @click="mapClick" @wheel.prevent="wheel" @pointerdown="panStart" @pointermove="pointerMove" @pointerup="pointerEnd" @pointercancel="pointerCancel">
                <div v-if="currentMap" class="map-surface" :style="surfaceStyle">
                  <img :src="currentMap.imageUrl" :alt="currentMap.name" draggable="false" />
                  <button v-for="marker in markers" :key="marker.id" class="map-marker" :class="{ finding: activeFor(marker.productId), station: marker.kind === 'base', 'search-hit': focusedProduct === marker.productId && marker.kind === 'product' }" :style="{ left: marker.x * 100 + '%', top: marker.y * 100 + '%' }" @pointerdown.stop="markerStart($event, marker)" @click.stop="markerClick(marker)" @mouseenter="hoverMarker(marker)" @mouseleave="hoveredMarker = ''" @focus="hoverMarker(marker)" @blur="hoveredMarker = ''" :aria-label="marker.label">
                    <Icon :type="marker.kind === 'base' ? 'md-radio' : 'md-pin'" />
                  </button>
                </div>
                <div v-if="bubble" class="marker-bubble" role="tooltip" :style="bubbleStyle"><strong>{{ bubble.label }}</strong><p>{{ warehouseName(bubble.warehouseId) }} / {{ areaName(bubble.areaId) }}</p><p v-if="bubble.shelf">{{ tr("货架") }} · {{ bubble.shelf }}</p></div>
                <div v-if="!currentMap" class="empty-map"><Icon type="ios-map-outline" size="64" /><p>{{ tr("先选择仓库，再上传你画好的地图") }}</p><Button v-if="!mobile" type="primary" @click="openUpload">{{ tr("上传 PNG / JPG 地图") }}</Button></div>
              </div>
              <div class="map-footer">{{ currentMap ? currentMap.name + ' · ' + currentMap.width + ' × ' + currentMap.height : tr("尚未配置地图") }} {{ tr("· 位置来源：手动配置") }} <span>{{ tr("支持拖动平移、缩放") }}</span></div>
            </Card>
            <div class="right-panel">
              <Card class="sl-card product-card" :bordered="false" dis-hover><div slot="title" class="title"><span class="icon icon-3"></span><span>{{ tr("物品寻找") }}</span></div>
                <div class="batch-toolbar">
                  <Checkbox :value="allChecked" :disabled="!eligibleProducts.length || batchBusy" @on-change="checkVisible">{{ tr("全选") }}</Checkbox>
                  <span>{{ tr("已选") }} {{ checkedProducts.length }}</span>
                  <Button type="primary" :loading="batchBusy" :disabled="!checkedProducts.length" @click="findSelected">{{ tr("批量寻找") }}</Button>
                  <Button v-if="activeTasks.length" :disabled="batchBusy" @click="stopAll">{{ tr("停止全部") }} · {{ activeTasks.length }}</Button>
                </div>
                <div class="product-scroll"><div v-for="p in filteredProducts" :key="p.id" class="product-row" :class="{ selected: selected === 'product:' + p.id }" @click="selectProduct(p)">
                  <div class="product-heading"><Checkbox :value="checkedIds.includes(p.id)" :disabled="!p.beaconCode || batchBusy" @click.native.stop @on-change="checkProduct(p.id, $event)"><span class="sr-only">{{ tr("选择") }} {{ p.name }}</span></Checkbox><strong>{{ p.name }}</strong><Tag v-if="activeFor(p.id)" color="orange">{{ tr("寻找中") }}</Tag></div><p>{{ p.sku || tr("未设置 SKU") }} · {{ areaName(p.areaId) }}</p><p>{{ tr("信标：") }}{{ p.beaconCode || tr("未绑定") }} · {{ placementFor(p.id) ? tr("已配置位置") : tr("待配置位置") }}</p>
                  <div class="row-actions"><Button size="small" type="primary" :disabled="!p.beaconCode" @click.stop="find(p)">{{ tr("寻找") }}</Button><Button v-if="activeFor(p.id)" size="small" type="error" @click.stop="stop(activeFor(p.id))">{{ tr("停止") }}</Button><Button size="small" @click.stop="focus(p)">{{ tr("查看位置") }}</Button></div>
                </div><p v-if="!filteredProducts.length" class="empty-text">{{ tr("等待手机同步商品，或在商品管理中添加") }}</p></div>
                <template v-if="editing"><Divider>{{ tr("放置基站") }}</Divider><Button v-for="b in state.bases" :key="b.id" size="small" :type="selected === 'base:' + b.id ? 'primary' : 'default'" @click="selected = 'base:' + b.id">{{ b.name || b.sn }}</Button></template>
              </Card>
              <Card v-if="mobile" class="sl-card task-card" :bordered="false" dis-hover><div slot="title" class="title"><span class="icon icon-7"></span><span>{{ tr("寻找状态") }}</span></div><Button v-if="mobile" slot="extra" type="text" @click="showHistory = !showHistory">{{ tr(showHistory ? "收起" : "展开") }}</Button><div v-if="!mobile || showHistory"><div v-for="t in recentTasks" :key="t.id" class="task-row"><strong>{{ taskName(t) }}</strong><Tag :color="t.status === 'failed' ? 'error' : 'blue'">{{ statusName(t.status) }}</Tag><p>{{ tr(t.message) }}</p><Button size="small" @click="focusTask(t)">{{ tr("查看位置") }}</Button><Button v-if="canStop(t)" size="small" @click="stop(t)">{{ tr("停止寻找") }}</Button></div><p v-if="!recentTasks.length" class="empty-text">{{ tr("暂无寻找任务") }}</p></div><p v-else class="task-summary">{{ tr("寻找中") }} · {{ activeTasks.length }}</p></Card>
            </div>
            <Card v-if="!mobile" class="sl-card task-overview" :bordered="false" dis-hover>
              <div slot="title" class="title"><span class="icon icon-7"></span><span>{{ tr("寻找状态一览") }}</span></div>
              <component v-for="section in taskSections" :key="section.history ? 'history' : 'current'" :is="section.history ? 'details' : 'div'" :class="{ 'history-fold': section.history }">
                <summary v-if="section.history" class="history-summary">{{ tr("更多历史任务") }} · {{ section.runs.length }} <span>{{ tr("展开") }} ⌄</span></summary>
                <details v-for="run in section.runs" :key="run.id" class="run-row">
                <summary class="run-summary">
                  <div class="run-heading"><strong>{{ tr("寻找任务") }} #{{ run.id.slice(-6) }}</strong><span>{{ run.items.length }} {{ tr("个商品") }} · {{ formatTime(run.createdAt) }} · {{ tr(run.origin.startsWith('phone') ? '手机' : '电脑') }}</span></div>
                  <Tag :color="runStatus(run) === '存在异常' ? 'error' : runStatus(run) === '进行中' ? 'blue' : 'default'">{{ tr(runStatus(run)) }}</Tag>
                  <div class="run-progress"><span>{{ run.items.map(item => item.name).join('、') }}</span><small>{{ runCounts(run) }}</small></div>
                  <Button v-if="run.items.some(item => item.task && !terminal(item.task))" size="small" @click.stop.prevent="stopRun(run)">{{ tr("停止任务") }}</Button>
                  <span class="run-expand">{{ tr("查看明细") }} ⌄</span>
                </summary>
                <div class="run-items"><div v-for="(item, index) in run.items" :key="index" class="run-item"><strong>{{ item.name }}</strong><Tag :color="item.status === 'failed' ? 'error' : 'blue'">{{ statusName(item.status) }}</Tag><span>{{ tr(item.error || item.task?.message || '历史记录') }}</span><Button v-if="item.productId" size="small" @click="focusTask(item)">{{ tr("查看位置") }}</Button><Button v-if="item.task && canStop(item.task)" size="small" @click="stop(item.task)">{{ tr("停止") }}</Button></div></div>
                </details>
              </component>
              <p v-if="!taskRuns.length" class="empty-text">{{ tr("暂无寻找任务") }}</p>
            </Card>
          </div>
          <Card v-else-if="page === 'products'" class="sl-card" :bordered="false" dis-hover>
            <div slot="title" class="title"><span class="icon icon-3"></span><span>{{ tr("商品管理") }}</span></div><Button slot="extra" type="primary" @click="editProduct()">{{ tr("添加商品") }}</Button>
            <Input v-model="query" search :placeholder="tr('商品名 / SKU / 信标码')" class="table-search" />
            <p class="table-hint">{{ tr("点击商品行编辑资料与绑定") }}</p>
            <Table :columns="localizedProductColumns" :data="productTableRows" @on-row-click="editProductRow" />
          </Card>
          <Card v-else-if="page === 'warehouses'" class="sl-card" :bordered="false" dis-hover><div slot="title" class="title"><span class="icon icon-1"></span><span>{{ tr("仓库与区域") }}</span></div><Button slot="extra" type="primary" @click="editWarehouse()">{{ tr("添加仓库") }}</Button>
            <div v-for="w in state.warehouses" :key="w.id" class="warehouse-row"><strong>{{ w.name }}</strong><Button size="small" @click="editWarehouse(w)">{{ tr("改名") }}</Button><Button size="small" @click="editArea(w.id)">{{ tr("添加区域") }}</Button><div><Tag v-for="a in state.areas.filter(a => a.warehouseId === w.id)" :key="a.id" @click.native="editArea(w.id, a)">{{ a.name }}</Tag></div></div>
          </Card>
          <Card v-else-if="page === 'devices'" class="sl-card" :bordered="false" dis-hover><div slot="title" class="title"><span class="icon icon-7"></span><span>{{ tr("基站与信标") }}</span></div><Alert show-icon>{{ tr("设备由手机扫码登记并同步。蓝牙控制需要手机桥接服务在线；LAN 基站按登记的编号连接。") }}</Alert><Table :columns="localizedBeaconColumns" :data="state.beacons" /><Divider>{{ tr("桥接设备") }}</Divider><div v-for="c in state.clients" :key="c.id"><Tag :color="onlineClients.includes(c) ? 'success' : 'default'">{{ onlineClients.includes(c) ? tr("在线") : tr("离线") }}</Tag>{{ c.id }} · {{ c.bases.join('、') }}</div></Card>
          <Card v-else class="sl-card" :bordered="false" dis-hover><div slot="title" class="title"><span class="icon icon-7"></span><span>{{ tr("系统设置") }}</span></div>
            <Form :label-width="100"><FormItem :label="tr('服务地址')"><Input :value="lanAddresses[0] || origin" readonly /><p>{{ tr("手机填写这台电脑的局域网 IP，端口") }} {{ port }}{{ tr("，与 PC 在同一网络。") }}</p></FormItem><FormItem :label="tr('连接密钥')"><Input v-model="keyDraft" type="password" password :placeholder="tr('填入共享服务密钥')" /><Button type="primary" @click="saveKey">{{ tr("保存并连接") }}</Button><Button @click="copyKey">{{ tr("复制密钥") }}</Button></FormItem><FormItem :label="tr('点灯颜色')"><Select v-model="color"><Option v-for="c in colors" :key="c.value" :value="c.value">{{ tr(c.name) }}</Option></Select></FormItem><FormItem :label="tr('持续时间')"><InputNumber v-model="durationSec" :min="3" :max="381" /> {{ tr("秒") }}</FormItem><FormItem :label="tr('闪烁')"><i-switch v-model="flash" /></FormItem><FormItem :label="tr('蜂鸣')"><i-switch v-model="beep" /></FormItem></Form>
            <Alert show-icon>{{ tr("上传地图和配置位置保存在 PC 服务数据库，手机自动读取。地图标记表示配置位置；实物移动后请重新调整。任务状态以控制桥接回执为准。") }}</Alert><Button @click="backup">{{ tr("导出共享资料备份") }}</Button>
          </Card>
        </main>
      </div>
    </div>
    <Modal v-model="uploadOpen" :title="tr('上传仓库地图')" :loading="uploading" @on-ok="upload">
      <Form :label-width="80"><FormItem :label="tr('仓库')"><Select v-model="uploadForm.warehouseId"><Option v-for="w in state.warehouses" :key="w.id" :value="w.id">{{ w.name }}</Option></Select></FormItem><FormItem :label="tr('地图名称')"><Input v-model="uploadForm.name" /></FormItem><FormItem :label="tr('楼层')"><Input v-model="uploadForm.floor" /></FormItem><FormItem :label="tr('图片')"><input ref="imageFile" type="file" accept="image/png,image/jpeg" @change="readImage" /><p>{{ uploadForm.width ? uploadForm.width + ' × ' + uploadForm.height : tr("上传你自己画好的 PNG / JPG") }}</p></FormItem></Form>
    </Modal>
    <Modal v-model="productOpen" :title="tr('商品与信标绑定')" :loading="saving" @on-ok="saveProduct"><Form :label-width="80"><FormItem :label="tr('商品名称')"><Input v-model="productForm.name" /></FormItem><FormItem label="SKU"><Input v-model="productForm.sku" /></FormItem><FormItem :label="tr('仓库')"><Select v-model="productForm.warehouseId" @on-change="productForm.areaId = ''; productForm.shelf = ''"><Option v-for="w in state.warehouses" :key="w.id" :value="w.id">{{ w.name }}</Option></Select></FormItem><FormItem :label="tr('区域')"><Select v-model="productForm.areaId" clearable @on-change="productForm.shelf = ''"><Option v-for="a in state.areas.filter(a => a.warehouseId === productForm.warehouseId)" :key="a.id" :value="a.id">{{ a.name }}</Option></Select></FormItem><FormItem :label="tr('货架')"><AutoComplete v-model="productForm.shelf" :data="shelfSuggestions" :disabled="!productForm.areaId" :placeholder="tr('先选区域，再填写或选择货架')" :maxlength="80" /></FormItem><FormItem :label="tr('信标')"><Select v-model="productForm.beaconCode" clearable filterable><Option v-for="b in uniqueBeacons" :key="b.code" :value="b.code">{{ b.name }} · {{ b.code }}</Option></Select></FormItem><FormItem :label="tr('备注')"><Input v-model="productForm.memo" /></FormItem></Form><Button v-if="productForm.version" type="error" @click="deleteProduct(productForm); productOpen = false">{{ tr("删除商品") }}</Button></Modal>
    <Modal v-model="nameOpen" :title="nameForm.kind === 'areas' ? tr('区域名称') : tr('仓库名称')" :loading="saving" @on-ok="saveName"><Input v-model="nameForm.name" :placeholder="tr('填写名称')" /></Modal>
  </div>
</template>

<script>
import { translate } from './i18n';
import ViewUI from 'view-design';
import zhLocale from 'view-design/dist/locale/zh-CN';
import jaLocale from 'view-design/dist/locale/ja-JP';

const emptyState = () => ({ warehouses: [], areas: [], products: [], beacons: [], bases: [], placements: [], maps: [], tasks: [], taskRuns: [], clients: [], revision: 0, serverTime: Date.now() });
const id = () => crypto.getRandomValues(new Uint32Array(4)).join('');
export default {
  data() {
    return { lang: new URLSearchParams(location.search).get('lang') || localStorage.getItem('warehouseLanguage') || 'zh', state: emptyState(), mobile: new URLSearchParams(location.search).has('mobile'), page: 'map', hoveredMarker: '', popupMarker: '', focusedProduct: '', checkedIds: [], batchBusy: false, showHistory: false, query: '', warehouseId: '', mapId: '', selected: '', editing: false,
      zoom: 1, pan: { x: 0, y: 0 }, gesture: null, moved: false, error: '', connection: "正在连接", token: '', keyDraft: '', polling: null, busy: false,
      origin: location.origin, port: location.port || '80', lanAddresses: [], uploadOpen: false, uploading: false, uploadForm: {}, productOpen: false, productForm: {}, nameOpen: false, nameForm: {}, saving: false,
      color: 2, durationSec: 60, flash: true, beep: false,
      colors: [{ value: 4, name: "红" }, { value: 6, name: "黄" }, { value: 1, name: "蓝" }, { value: 2, name: "绿" }, { value: 3, name: "青" }, { value: 7, name: "白" }, { value: 5, name: "紫" }],
      menus: [{ id: 'map', name: "首页", icon: 'home' }, { id: 'products', name: "商品管理", icon: 'location' }, { id: 'devices', name: "设备管理", icon: 'device' }, { id: 'warehouses', name: "仓库管理", icon: 'system' }, { id: 'settings', name: "系统设置", icon: 'system' }],
      productColumns: [{ title: "商品名称", key: 'name', minWidth: 150 }, { title: 'SKU', key: 'sku', minWidth: 100 }, { title: "仓库 / 区域", key: 'warehouseArea', minWidth: 180 }, { title: "货架", key: 'shelfLabel', minWidth: 110 }, { title: "信标码", key: 'beaconCode', minWidth: 130 }],
      beaconColumns: [{ title: "信标名称", key: 'name' }, { title: "信标码", key: 'code' }, { title: "基站", key: 'baseSn' }] };
  },
  computed: {
    localizedProductColumns() { return this.productColumns.map(c => ({ ...c, title: this.tr(c.title) })); },
    productTableRows() { return this.allFilteredProducts.map(p => ({ ...p, warehouseArea: this.warehouseName(p.warehouseId) + ' / ' + this.areaName(p.areaId), shelfLabel: p.shelf || this.tr("未设置") })); },
    shelfSuggestions() { return [...new Set(this.state.products.filter(p => p.warehouseId === this.productForm.warehouseId && p.areaId === this.productForm.areaId && p.shelf).map(p => p.shelf))]; },
    bubble() { return this.markers.find(marker => marker.id === (this.hoveredMarker || this.popupMarker)); },
    bubbleStyle() {
      const point = this.bubble, box = this.$refs.viewport, image = box?.querySelector('img'); if (!point || !image) return {};
      const x = this.pan.x + point.x * image.clientWidth * this.zoom, y = this.pan.y + point.y * image.clientHeight * this.zoom;
      return { left: Math.max(4, Math.min(box.clientWidth - 244, x + (x + 256 > box.clientWidth ? -248 : 18))) + 'px', top: Math.max(4, Math.min(box.clientHeight - 110, y + 18)) + 'px' };
    },
    localizedBeaconColumns() { return this.beaconColumns.map(c => ({ ...c, title: this.tr(c.title) })); },
    warehouseMaps() { return this.state.maps.filter(m => m.warehouseId === this.warehouseId); },
    currentMap() { return this.state.maps.find(m => m.id === this.mapId); },
    warehouseProducts() { return this.state.products.filter(p => !this.warehouseId || p.warehouseId === this.warehouseId); },
    allFilteredProducts() { return this.state.products.filter(p => [p.name, p.sku, p.beaconCode, p.shelf, this.areaName(p.areaId)].join(' ').toLowerCase().includes(this.query.toLowerCase())); },
    filteredProducts() { return this.allFilteredProducts.filter(p => !this.warehouseId || p.warehouseId === this.warehouseId); },
    eligibleProducts() { return this.filteredProducts.filter(p => p.beaconCode); },
    checkedProducts() { return this.state.products.filter(p => p.beaconCode && this.checkedIds.includes(p.id)); },
    allChecked() { return this.eligibleProducts.length > 0 && this.eligibleProducts.every(p => this.checkedIds.includes(p.id)); },
    activeTasks() { return this.state.tasks.filter(t => !this.terminal(t) && (!this.warehouseId || t.warehouseId === this.warehouseId)); },
    uniqueBeacons() { return this.state.beacons.filter((b, i, a) => a.findIndex(x => x.code === b.code) === i); },
    onlineClients() { return this.state.clients.filter(c => this.state.serverTime - c.lastSeen < 8000); },
    taskRuns() {
      const commands = new Map(this.state.tasks.map(task => [task.id, task]));
      const represented = new Set((this.state.taskRuns || []).flatMap(run => run.items.map(item => item.taskId)));
      const legacy = this.state.tasks.filter(task => !represented.has(task.id)).map(task => ({ id: task.id, createdAt: task.createdAt || task.updatedAt, origin: task.origin || 'pc', items: [{ taskId: task.id, productId: task.productId, name: this.taskName(task), warehouseId: task.warehouseId }] }));
      return [...(this.state.taskRuns || []), ...legacy].filter(run => !this.warehouseId || run.items.some(item => item.warehouseId === this.warehouseId)).map(run => ({ ...run, items: run.items.map(item => { const task = commands.get(item.taskId); return { ...item, task, status: item.error ? 'failed' : task?.status || 'archived' }; }) })).sort((a, b) => b.createdAt - a.createdAt);
    },
    taskSections() {
      const active = this.taskRuns.filter(run => run.items.some(item => item.task && !this.terminal(item.task)));
      const history = this.taskRuns.filter(run => !run.items.some(item => item.task && !this.terminal(item.task)));
      return [{ history: false, runs: [...active, ...history.slice(0, 3)] }, ...(history.length > 3 ? [{ history: true, runs: history.slice(3) }] : [])];
    },
    recentTasks() { return [...this.state.tasks].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 8); },
    selectedLabel() { const [kind, ident] = this.selected.split(':'); const entity = (kind === 'base' ? this.state.bases : this.state.products).find(e => e.id === ident); return entity ? this.tr("当前：") + entity.name : ''; },
    markers() { return this.state.placements.filter(p => p.mapId === this.mapId).map(p => { const e = (p.kind === 'base' ? this.state.bases : this.state.products).find(e => e.id === p.entityId); return { ...p, label: e ? e.name : this.tr("已删除物品"), warehouseId: e?.warehouseId || this.currentMap?.warehouseId || '', areaId: e?.areaId || '', shelf: e?.shelf || '', productId: p.kind === 'base' ? '' : p.entityId }; }).filter(p => p.label !== this.tr("已删除物品")); },
    surfaceStyle() { return { width: '100%', transformOrigin: '0 0', transform: `translate(${this.pan.x}px,${this.pan.y}px) scale(${this.zoom})` }; }
  },
  watch: {
    query(value) { if (!value.trim()) { this.focusedProduct = ''; this.popupMarker = ''; } },
    page() { this.hoveredMarker = ''; this.popupMarker = ''; },
    lang: { immediate: true, handler(value) { ViewUI.locale(value === 'ja' ? jaLocale : zhLocale); document.documentElement.lang = value; document.title = this.tr('仓库定位系统'); } }
  },
  async mounted() {
    this.token = new URLSearchParams(location.hash.slice(1)).get('token') || sessionStorage.getItem('warehouseKey') || '';
    history.replaceState(null, '', location.pathname + location.search);
    if (!this.token) { try { const r = await fetch('/api/session'); if (r.ok) this.token = (await r.json()).token; } catch (e) { this.error = e.message; } }
    this.keyDraft = this.token; sessionStorage.setItem('warehouseKey', this.token);
    if (!this.token) this.page = 'settings';
    await this.refresh();
    try { this.lanAddresses = (await this.api('network')).addresses; } catch (e) { /* Older running services still show their current origin. */ }
    this.polling = setInterval(() => this.refresh(), 1200);
  },
  beforeDestroy() { clearInterval(this.polling); },
  methods: {
    tr(value) { return translate(value, this.lang); },
    saveLanguage() { localStorage.setItem('warehouseLanguage', this.lang); },
    async api(path, body) { const r = await fetch('/api/' + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'X-Access-Key': this.token, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) }); const data = await r.json(); if (!r.ok) throw new Error(data.error || this.tr("请求失败")); return data; },
    async action(fn) { try { this.error = ''; return await fn(); } catch (e) { this.error = e.message; this.$Message.error(e.message); return null; } },
    async refresh() {
      if (!this.token || this.busy || this.gesture) return;
      this.busy = true;
      try {
        const next = await this.api('state');
        if (this.gesture) return;
        this.state = next; this.connection = this.tr("共享服务已连接");
        if (!this.warehouseId || !next.warehouses.some(w => w.id === this.warehouseId)) { this.warehouseId = next.warehouses[0]?.id || ''; this.selectWarehouse(); }
        if (!this.currentMap) this.mapId = this.warehouseMaps[0]?.id || '';
        this.checkedIds = this.checkedIds.filter(id => next.products.some(p => p.id === id && p.beaconCode));
      } catch (e) { this.connection = this.tr("连接中断：") + e.message; }
      finally { this.busy = false; }
    },
    selectWarehouse() { this.hoveredMarker = ''; this.popupMarker = ''; this.focusedProduct = ''; this.checkedIds = []; this.mapId = this.warehouseMaps[0]?.id || ''; this.resetView(); },
    resetView() { this.zoom = 1; this.pan = { x: 0, y: 0 }; },
    setZoom(value) {
      const next = Math.max(0.25, Math.min(5, value)), box = this.$refs.viewport;
      if (box) { const scale = next / this.zoom, x = box.clientWidth / 2, y = box.clientHeight / 2; this.pan = { x: x - (x - this.pan.x) * scale, y: y - (y - this.pan.y) * scale }; }
      this.zoom = next;
    },
    wheel(e) { this.setZoom(this.zoom * (e.deltaY < 0 ? 1.12 : 0.89)); },
    searchLocation() {
      if (!this.query.trim()) { this.resetView(); return; }
      const query = this.query.trim().toLowerCase();
      const product = this.filteredProducts.find(p => [p.name, p.sku, p.beaconCode].some(value => String(value || '').toLowerCase() === query)) || this.filteredProducts[0];
      if (product) this.focus(product); else this.$Message.info(this.tr("未找到商品"));
    },
    placementFor(productId) { return this.state.placements.find(p => p.kind === 'product' && p.entityId === productId && this.state.maps.some(m => m.id === p.mapId && m.warehouseId === this.state.products.find(e => e.id === productId)?.warehouseId)); },
    warehouseName(ident) { return this.state.warehouses.find(w => w.id === ident)?.name || this.tr("未分配"); },
    areaName(ident) { return this.state.areas.find(a => a.id === ident)?.name || this.tr("未分配区域"); },
    terminal(t) { return ['failed', 'stopped', 'expired'].includes(t.status); },
    canStop(t) { return t.status !== 'stopped' && !this.state.tasks.some(other => other.id !== t.id && other.beaconCode === t.beaconCode && !this.terminal(other)); },
    activeFor(productId) { return productId && this.state.tasks.find(t => t.productId === productId && !this.terminal(t)); },
    focusTask(t) { const p = this.state.products.find(p => p.id === t.productId); if (p) this.focus(p); },
    taskName(t) { return this.state.products.find(p => p.id === t.productId)?.name || t.beaconCode; },
    statusName(s) { return ({ queued: this.tr("等待桥接"), dispatching: this.tr("正在下发"), sent: this.tr("SDK 已发送"), accepted: this.tr("基站已接收"), stop_pending: this.tr("正在消灯"), stopped: this.tr("已发送消灯"), failed: this.tr("执行失败"), expired: this.tr("已到期"), archived: this.tr("已归档") })[s] || s; },
    formatTime(time) { return new Intl.DateTimeFormat(this.lang === 'ja' ? 'ja-JP' : 'zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(time)); },
    runStatus(run) {
      if (run.items.some(item => item.status === 'failed' || item.task?.status === 'expired' && /未下发|待确认/.test(item.task.message))) return '存在异常';
      if (run.items.some(item => item.task && !this.terminal(item.task))) return '进行中';
      return run.items.every(item => item.status === 'stopped') ? '已停止' : '已结束';
    },
    runCounts(run) { const counts = {}; run.items.forEach(item => counts[item.status] = (counts[item.status] || 0) + 1); return Object.entries(counts).map(([status, count]) => this.statusName(status) + ' ' + count).join(' · '); },
    async stopRun(run) { const tasks = [...new Map(run.items.filter(item => item.task && !this.terminal(item.task)).map(item => [item.task.id, item.task])).values()]; for (const task of tasks) await this.action(() => this.api('tasks/' + task.id + '/stop', {})); await this.refresh(); },
    selectProduct(p) { this.selected = 'product:' + p.id; if (!this.editing) this.focus(p); },
    focus(p, zoomIn = true) {
      const point = this.placementFor(p.id), previousMap = this.mapId;
      this.page = 'map'; this.warehouseId = p.warehouseId; this.selected = 'product:' + p.id;
      this.mapId = point ? point.mapId : this.warehouseMaps[0]?.id || '';
      if (this.mapId !== previousMap) this.resetView();
      if (!point) { this.$Message.info(this.tr("该商品还未配置地图位置")); return; }
      if (zoomIn) this.$nextTick(() => {
        if (this.mapId !== point.mapId || this.selected !== 'product:' + p.id) return;
        this.focusedProduct = p.id; this.popupMarker = point.id; this.hoveredMarker = '';
        const box = this.$refs.viewport, image = box?.querySelector('img'); if (!image) return;
        this.zoom = 1.6; this.pan = { x: box.clientWidth / 2 - point.x * image.clientWidth * this.zoom, y: box.clientHeight / 2 - point.y * image.clientHeight * this.zoom };
      });
    },
    findPayload(p) { return { productId: p.id, origin: this.mobile ? 'phone-map' : 'pc', color: this.color, durationSec: this.durationSec, flash: this.flash, beep: this.beep }; },
    async find(p) { await this.action(async () => { await this.api('find', this.findPayload(p)); this.editing = false; await this.refresh(); this.focus(p, false); }); },
    checkProduct(id, checked) { this.checkedIds = checked ? [...new Set([...this.checkedIds, id])] : this.checkedIds.filter(value => value !== id); },
    checkVisible(checked) { this.eligibleProducts.forEach(p => this.checkProduct(p.id, checked)); },
    async findSelected() {
      if (this.batchBusy) return;
      const products = [...this.checkedProducts]; if (!products.length) return; this.batchBusy = true; this.editing = false;
      const run = await this.action(() => this.api('find-batch', { ...this.findPayload(products[0]), productIds: products.map(p => p.id) }));
      if (run) { run.items.filter(item => !item.error).forEach(item => this.checkProduct(item.productId, false)); if (run.items.some(item => item.error)) this.$Message.error(this.tr("部分商品寻找失败，请查看任务明细")); }
      this.batchBusy = false; await this.refresh();
    },
    async stopAll() {
      if (this.batchBusy) return;
      const tasks = [...this.activeTasks]; this.batchBusy = true;
      for (const task of tasks) await this.action(() => this.api('tasks/' + task.id + '/stop', {}));
      this.batchBusy = false; await this.refresh();
    },
    async stop(t) { await this.action(async () => { await this.api('tasks/' + t.id + '/stop', {}); await this.refresh(); }); },
    panStart(e) { if (!this.currentMap || e.button > 0) return; this.moved = false; this.gesture = { type: 'pan', startX: e.clientX, startY: e.clientY, x: this.pan.x, y: this.pan.y }; e.currentTarget.setPointerCapture(e.pointerId); },
    markerStart(e, marker) { this.moved = false; if (!this.editing) return; this.selected = marker.kind + ':' + marker.entityId; e.preventDefault(); this.gesture = { type: 'marker', marker: { ...marker }, startX: e.clientX, startY: e.clientY }; this.$refs.viewport.setPointerCapture(e.pointerId); },
    pointerMove(e) { const g = this.gesture; if (!g) return; if (Math.hypot(e.clientX - g.startX, e.clientY - g.startY) > 4) this.moved = true; if (g.type === 'pan') this.pan = { x: g.x + e.clientX - g.startX, y: g.y + e.clientY - g.startY }; else if (this.moved) { const xy = this.coords(e); const real = this.state.placements.find(p => p.id === g.marker.id); if (real && xy) Object.assign(real, xy); } },
    async pointerEnd() { const g = this.gesture; this.gesture = null; if (g?.type === 'marker' && this.moved) { const point = this.state.placements.find(p => p.id === g.marker.id); const ok = await this.action(() => this.change('placements', point)); if (!ok) Object.assign(point, g.marker); } },
    pointerCancel() { const g = this.gesture; if (g?.type === 'marker') Object.assign(this.state.placements.find(p => p.id === g.marker.id), g.marker); this.gesture = null; },
    coords(e) { const image = this.$refs.viewport?.querySelector('img'); if (!image) return null; const box = image.getBoundingClientRect(); const x = (e.clientX - box.left) / box.width, y = (e.clientY - box.top) / box.height; return x >= 0 && x <= 1 && y >= 0 && y <= 1 ? { x, y } : null; },
    async place(e) { if (!this.editing || !this.selected || this.moved) return; const xy = this.coords(e); if (!xy) return; const [kind, entityId] = this.selected.split(':'); if (kind === 'product' && this.state.products.find(p => p.id === entityId)?.warehouseId !== this.warehouseId) return; const existing = this.state.placements.find(p => p.kind === kind && p.entityId === entityId); await this.action(() => this.change('placements', { ...(existing || {}), id: existing?.id || id(), kind, entityId, mapId: this.mapId, ...xy })); },
    hoverMarker(marker) { if (!this.mobile && !this.editing) this.hoveredMarker = marker.id; },
    markerClick(marker) { if (this.moved) return; this.selected = marker.kind + ':' + marker.entityId; if (this.mobile && !this.editing) this.popupMarker = this.popupMarker === marker.id ? '' : marker.id; },
    mapClick(e) { if (!this.moved) { this.popupMarker = ''; this.hoveredMarker = ''; } this.place(e); },
    dismissBubble(e) { if (!e.target.closest('.map-marker, .map-toolbar, .product-row, .run-item')) { this.popupMarker = ''; this.hoveredMarker = ''; } },
    editProductRow(row) { this.editProduct(this.state.products.find(p => p.id === row.id)); },
    async change(kind, data, remove = false) { const change = { kind, id: data.id, expectedVersion: data.version || 0, data, delete: remove }; this.state = await this.api('sync', { changes: [change] }); return true; },
    openUpload() { this.uploadForm = { warehouseId: this.warehouseId, name: this.tr("仓库地图"), floor: '1F', image: '', width: 0, height: 0 }; this.uploadOpen = true; if (this.$refs.imageFile) this.$refs.imageFile.value = ''; },
    async readImage(e) { const file = e.target.files[0]; if (!file) return; await this.action(async () => { if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 15 * 1024 * 1024) throw new Error(this.tr("请选择 15MB 以内的 PNG / JPG")); const raw = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); }); const img = new Image(); await new Promise((resolve, reject) => { img.onload = resolve; img.onerror = () => reject(new Error(this.tr("图片读取失败"))); img.src = raw; }); Object.assign(this.uploadForm, { image: raw, width: img.naturalWidth, height: img.naturalHeight }); }); },
    async upload() { this.uploading = true; const result = await this.action(async () => { if (!this.uploadForm.warehouseId || !this.uploadForm.image) throw new Error(this.tr("请选择仓库和图片")); this.state = await this.api('maps', this.uploadForm); this.warehouseId = this.uploadForm.warehouseId; this.mapId = this.state.maps[this.state.maps.length - 1].id; this.resetView(); return true; }); this.uploading = false; if (result) this.uploadOpen = false; },
    editProduct(p) { this.productForm = { id: id(), name: '', sku: '', warehouseId: this.warehouseId || this.state.warehouses[0]?.id || '', areaId: '', shelf: '', beaconCode: '', memo: '', ...(p || {}) }; this.productOpen = true; },
    async saveProduct() { this.saving = true; const result = await this.action(() => this.change('products', { ...this.productForm, areaId: this.productForm.areaId || '', shelf: (this.productForm.shelf || '').trim(), beaconCode: this.productForm.beaconCode || '', updatedAt: Date.now() })); this.saving = false; if (result) this.productOpen = false; },
    deleteProduct(p) { this.$Modal.confirm({ title: this.tr("删除商品"), content: this.tr("确认删除此商品及其地图标记？"), onOk: () => this.action(async () => { const changes = [{ kind: 'products', id: p.id, expectedVersion: p.version, delete: true }, ...this.state.placements.filter(x => x.kind === 'product' && x.entityId === p.id).map(x => ({ kind: 'placements', id: x.id, expectedVersion: x.version, delete: true }))]; this.state = await this.api('sync', { changes }); }) }); },
    editWarehouse(w) { this.nameForm = { kind: 'warehouses', id: id(), name: '', createdAt: Date.now(), ...(w || {}) }; this.nameOpen = true; },
    editArea(warehouseId, a) { this.nameForm = { kind: 'areas', id: id(), name: '', warehouseId, createdAt: Date.now(), ...(a || {}) }; this.nameOpen = true; },
    async saveName() { this.saving = true; const { kind, ...data } = this.nameForm; const result = await this.action(() => this.change(kind, data)); this.saving = false; if (result) this.nameOpen = false; },
    saveKey() { this.token = this.keyDraft.trim(); sessionStorage.setItem('warehouseKey', this.token); this.refresh(); },
    copyKey() { if (navigator.clipboard) navigator.clipboard.writeText(this.token).then(() => this.$Message.success(this.tr("已复制")), () => this.$Message.info(this.tr("点击密码显示按钮后手动复制"))); else this.$Message.info(this.tr("点击密码显示按钮后手动复制")); },
    backup() { const blob = new Blob([JSON.stringify(this.state, null, 2)], { type: 'application/json' }); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'warehouse-backup.json'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
  }
};
</script>

<style lang="less" scoped>
/* Reuse the original AOABLUETOOTH shell and home-card styles verbatim. */
@import '@/components/SlLayoutIview/index.less';
@import '@/components/SlLayoutIview/components/HeaderBar/index.less';
@import '@/components/SlLayoutIview/components/SideBar/index.less';
@import '@/views/home/index.less';
.map-layout { display: flex; gap: 14px; align-items: flex-start; }
.map-card { flex: 2; min-width: 0; }
.right-panel { flex: 1; min-width: 290px; display: flex; flex-direction: column; gap: 14px; }
.select-wh { width: 140px; margin-left: 10px; }.select-map { width: 140px; margin-left: 8px; }
.map-toolbar { display: flex; gap: 6px; margin-bottom: 12px; align-items: center; .ivu-input-wrapper { flex: 1; } }
.map-viewport { height: 62vh; min-height: 360px; overflow: hidden; background: #edf2f7; position: relative; touch-action: none; }
.map-surface { position: relative; img { width: 100%; display: block; user-select: none; pointer-events: none; } }
.map-marker { position: absolute; transform: translate(-50%, -50%); border: 0; border-radius: 50%; background: #008afb; color: white; width: 30px; height: 30px; cursor: pointer; font-size: 21px; padding: 0; touch-action: none;
  span { position: absolute; top: 34px; left: 50%; transform: translateX(-50%); white-space: nowrap; font-size: 12px; line-height: 22px; color: #333; background: white; padding: 0 6px; border-radius: 3px; box-shadow: 0 1px 5px #0002; }
  &.station { background: #576b83; } &.finding { background: #ff9200; animation: pulse 1.1s infinite; }
}
@keyframes pulse { 0% { box-shadow: 0 0 0 0 #ff920077; } 100% { box-shadow: 0 0 0 25px #ff920000; } }
.map-footer { padding-top: 12px; font-size: 12px; color: #888; span { float: right; } }
.empty-map { text-align: center; padding-top: 100px; color: #9da9b8; p { margin: 15px; } }
.product-scroll { max-height: 400px; overflow: auto; }.product-row { padding: 12px; border-bottom: 1px solid #eef1f5; cursor: pointer; p { font-size: 12px; color: #8b929b; margin: 5px 0; } &.selected { background: #eef7ff; } }
.row-actions { display: flex; gap: 6px; margin-top: 8px; }.task-row { padding: 10px 0; border-bottom: 1px solid #eee; p { color: #7c8793; margin: 6px 0; font-size: 12px; } .ivu-tag { margin-left: 8px; } }
.empty-text { padding: 15px 0; color: #919da9; }.table-search { max-width: 380px; margin-bottom: 15px; }.warehouse-row { padding: 20px; border-bottom: 1px solid #eee; strong { margin-right: 20px; } button { margin-right: 8px; } > div { margin-top: 12px; } }
.list-detail { display: flex; text-align: center; > div { flex: 1; } p:first-child { color: #7f8a98; font-size: 13px; } p:last-child { color: #008afb; font-size: 28px; margin-top: 8px; } }
@media (max-width: 900px) { .map-layout { flex-direction: column; }.map-card, .right-panel { width: 100%; flex: auto; }.main-side-bar { width: 170px; min-width: 170px; flex-basis: 170px; }.extra { flex-wrap: wrap; } }
.mobile { .main-side-bar { display: none; }.main-header-bar { padding: 0 10px; .name { font-size: 15px; }.logo { width: 20px; height: 20px; }.date-time { display: none; } }.main { padding: 8px; }.map-viewport { height: 48vh; min-height: 260px; }.map-toolbar { flex-wrap: wrap; }.map-toolbar .ivu-input-wrapper { flex-basis: 100%; }.sl-card .title { font-size: 15px; }.select-wh, .select-map { width: 106px; }.map-footer span { display: none; } }
</style>
<style lang="less">
html, body, #app { height: 100%; width: 100%; margin: 0; overflow: hidden; }
body { background: url('@/assets/images/bg.png') center center / cover no-repeat; }
.ivu-card { border-radius: 6px; }.ivu-card-head { padding: 14px 16px; }
</style>

<style scoped>
.language-select { margin-right: 12px; padding: 5px 24px 5px 10px; border: 1px solid #cbd5e1; border-radius: 4px; background-color: #fff; color: #515a6e; font: inherit; cursor: pointer; }
</style>

<style lang="less" scoped>
.batch-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 0 0 14px; border-bottom: 1px solid #edf1f5; font-size: 12px; color: #64748b; }
.product-heading { display: flex; align-items: center; gap: 6px; strong { flex: 1; font-size: 15px; } }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
.task-summary { color: #64748b; }.map-card { border-radius: 14px; }.product-card { border-radius: 14px; }
.mobile {
  background: #f4f6fa;
  .main-header-bar { display: none; }
  .sl-app-main-content-layout { height: 100%; }
  .main { padding: 12px; }
  .map-layout { gap: 12px; }
  .map-card, .right-panel { min-width: 0; }
  .map-card /deep/ .ivu-card-head { padding: 14px 12px; }
  .map-card /deep/ .ivu-card-body { padding: 12px; }
  .map-card .title { display: none; }
  .map-card .extra { position: static; display: flex; width: 100%; gap: 8px; }
  .select-wh, .select-map { flex: 1; width: auto; margin: 0; }
  .map-toolbar { gap: 6px; margin-bottom: 10px; }
  .map-toolbar /deep/ .ivu-input { border-radius: 10px; background: #f8fafc; }
  .map-viewport { height: 32vh; min-height: 210px; border-radius: 12px; }
  .map-footer { font-size: 11px; padding-top: 8px; }
  .overview-card { display: none; }
  .right-panel { gap: 12px; }
  .product-card /deep/ .ivu-card-head, .task-card /deep/ .ivu-card-head { padding: 14px 16px; }
  .product-card /deep/ .ivu-card-body { padding: 14px; }
  .product-scroll { max-height: none; }
  .product-row { padding: 14px 4px; }.product-row.selected { background: #f1f7ff; border-radius: 10px; }
  .product-row p { margin-left: 28px; }.row-actions { margin-left: 28px; }
  .row-actions /deep/ .ivu-btn { height: 32px; padding: 0 12px; border-radius: 8px; }
  .batch-toolbar /deep/ .ivu-btn { border-radius: 8px; }
  .task-card { border-radius: 14px; }
}
</style>

<style lang="less" scoped>
.map-layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr); gap: 16px; align-items: stretch; }
.right-panel { min-width: 0; }.right-panel .product-card { height: 100%; }
.product-scroll { max-height: 62vh; }.task-overview { grid-column-start: 1; grid-column-end: -1; border-radius: 14px; }
.run-note { color: #94a3b8; font-size: 12px; }.run-row { border-bottom: 1px solid #edf1f5; }.run-row:last-child { border-bottom: 0; }
.run-summary { display: flex; align-items: center; gap: 16px; padding: 16px 4px; cursor: pointer; list-style: none; }.run-summary::-webkit-details-marker { display: none; }
.run-heading { flex: 0 0 220px; strong { display: block; font-size: 14px; color: #233044; } > span { display: block; margin-top: 6px; color: #94a3b8; font-size: 12px; } }
.run-progress { flex: 1; min-width: 0; > span { display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #475569; } small { display: block; margin-top: 6px; color: #94a3b8; } }
.run-expand { flex-shrink: 0; color: #64748b; font-size: 12px; }.run-row[open] .run-expand { color: #2563eb; }
.run-items { padding: 0 16px 12px; background: #f8fafc; border-radius: 10px; }.run-item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid #edf1f5; strong { min-width: 100px; } > span:not(.ivu-tag) { flex: 1; color: #64748b; font-size: 12px; } }
@media (max-width: 900px) { .map-layout { grid-template-columns: minmax(0, 1fr); }.run-summary { flex-wrap: wrap; }.run-heading { flex-basis: 180px; } }
.mobile { .map-layout { display: flex; }.product-scroll { max-height: none; } }
</style>

<style lang="less" scoped>
.history-fold { margin-top: 12px; border: 1px solid #edf1f5; border-radius: 10px; background: #fafbfd; }
.history-summary { padding: 12px 16px; cursor: pointer; list-style: none; color: #64748b; font-size: 13px; span { float: right; font-size: 12px; } }
.history-summary::-webkit-details-marker { display: none; }
.history-fold[open] { max-height: 360px; overflow: auto; }
.history-fold .run-summary { padding: 10px 16px; gap: 12px; }
.history-fold .run-heading strong { font-size: 12px; }.history-fold .run-heading > span, .history-fold .run-progress { font-size: 11px; }
.history-fold .run-items { margin: 0 12px 10px; }
</style>

<style lang="less" scoped>
.map-marker.search-hit { background: #7c3aed; animation: none; box-shadow: 0 0 0 6px #7c3aed33; }
.marker-bubble { position: absolute; z-index: 20; width: 240px; padding: 14px 16px; border: 1px solid #ddd6fe; border-radius: 12px; background: #fff; color: #1e293b; box-shadow: 0 8px 28px #0f172a22; pointer-events: none; strong { display: block; font-size: 15px; } p { margin-top: 7px; color: #64748b; font-size: 12px; } }
.table-hint { margin-bottom: 12px; font-size: 12px; color: #94a3b8; }
</style>

<style scoped>
.marker-bubble::before { content: ''; position: absolute; top: -6px; left: 14px; width: 10px; height: 10px; background: #fff; border-top: 1px solid #ddd6fe; border-left: 1px solid #ddd6fe; transform: rotate(45deg); }
</style>
