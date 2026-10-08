/**
 * ORBIT INDOOR INTELLIGENCE - AoA High Precision RTL & Tag Finder
 * Based on Orbit CLE Design System & AOABluetooth Web Architecture
 */

// ─────────────────────────────────────────────────────────────
// 1. Constants & Configurations
// ─────────────────────────────────────────────────────────────

const STORAGE_KEY_DEVICES = 'orbit_cle_devices_v2';
const STORAGE_KEY_MAP = 'orbit_cle_custom_map';
const STORAGE_KEY_THEME = '__orbit_theme';
const STORAGE_KEY_LANG = '__orbit_lang';

// Warehouse Physical Dimensions (50.0m x 35.0m industrial space)
const WAREHOUSE_METERS_X = 50.0;
const WAREHOUSE_METERS_Y = 35.0;
const MAP_WIDTH = 2000;
const MAP_HEIGHT = 1400;

// Hardware 7-Color LED Palettes (matches BLE beacon firmware)
const LED_COLORS = [
  { id: 'green', nameZh: '翠绿', nameJa: '緑', nameEn: 'Green', hex: '#10B981' },
  { id: 'blue', nameZh: '天蓝', nameJa: '青', nameEn: 'Blue', hex: '#3B82F6' },
  { id: 'yellow', nameZh: '琥珀黄', nameJa: '黄', nameEn: 'Yellow', hex: '#F59E0B' },
  { id: 'cyan', nameZh: '青蓝', nameJa: 'シアン', nameEn: 'Cyan', hex: '#06B6D4' },
  { id: 'red', nameZh: '亮红', nameJa: '赤', nameEn: 'Red', hex: '#EF4444' },
  { id: 'purple', nameZh: '紫色', nameJa: '紫', nameEn: 'Purple', hex: '#A855F7' },
  { id: 'white', nameZh: '冷白', nameJa: '白', nameEn: 'White', hex: '#FFFFFF' }
];

// GA25 Base Stations (Industrial AoA Locators in 4 Quadrants)
const DEFAULT_LOCATORS = [
  {
    id: 'loc_1',
    name: 'LOC-GA25-01',
    model: 'GA25 AoA Array',
    zone: 'ZONE A',
    ip: '192.168.1.101',
    x: 25.5,
    y: 26.0,
    z: 3.8,
    status: 'ONLINE',
    coverageRadius: 180
  },
  {
    id: 'loc_2',
    name: 'LOC-GA25-02',
    model: 'GA25 AoA Array',
    zone: 'ZONE B',
    ip: '192.168.1.102',
    x: 74.5,
    y: 26.0,
    z: 3.8,
    status: 'ONLINE',
    coverageRadius: 180
  },
  {
    id: 'loc_3',
    name: 'LOC-GA25-03',
    model: 'GA25 AoA Array',
    zone: 'ZONE C',
    ip: '192.168.1.103',
    x: 25.5,
    y: 68.0,
    z: 3.8,
    status: 'ONLINE',
    coverageRadius: 180
  },
  {
    id: 'loc_4',
    name: 'LOC-GA25-04',
    model: 'GA25 AoA Array',
    zone: 'ZONE D',
    ip: '192.168.1.104',
    x: 74.5,
    y: 68.0,
    z: 3.8,
    status: 'ONLINE',
    coverageRadius: 180
  }
];

// Initial Demo Tags (Warehouse stock items with AoA beacons)
const DEFAULT_DEVICES = [
  {
    id: 'dev_1',
    name: '精密轴承 608ZZ',
    sku: 'SKU-1001',
    code: '0000001001',
    shelf: 'A棚-01',
    color: '#10B981',
    x: 24.5,
    y: 13.8
  },
  {
    id: 'dev_2',
    name: '工业手钻 18V',
    sku: 'SKU-2045',
    code: '0000001002',
    shelf: 'A棚-03',
    color: '#3B82F6',
    x: 32.0,
    y: 23.6
  },
  {
    id: 'dev_3',
    name: '动力锂电池组 4Ah',
    sku: 'SKU-3091',
    code: '0000001003',
    shelf: 'B棚-02',
    color: '#F59E0B',
    x: 68.5,
    y: 18.5
  },
  {
    id: 'dev_4',
    name: '高速光纤线缆 10m',
    sku: 'SKU-4410',
    code: '0000001004',
    shelf: 'B棚-05',
    color: '#06B6D4',
    x: 78.0,
    y: 35.0
  },
  {
    id: 'dev_5',
    name: '交流伺服电机',
    sku: 'SKU-5820',
    code: '0000001005',
    shelf: 'C棚-01',
    color: '#EF4444',
    x: 22.0,
    y: 55.2
  },
  {
    id: 'dev_6',
    name: '主控逻辑板 PCB-V2',
    sku: 'SKU-6102',
    code: '0000001006',
    shelf: 'C棚-04',
    color: '#A855F7',
    x: 36.5,
    y: 70.0
  },
  {
    id: 'dev_7',
    name: '重型减震脚轮 100mm',
    sku: 'SKU-7701',
    code: '0000001007',
    shelf: 'D棚-02',
    color: '#FFFFFF',
    x: 72.0,
    y: 60.5
  }
];

// ─────────────────────────────────────────────────────────────
// 2. Multilingual Dictionary (i18n: 中文 / 日本語 / English)
// ─────────────────────────────────────────────────────────────

const I18N = {
  'zh': {
    title: 'ORBIT - 室内高精度定位与标签寻物发光控制台',
    navRtl: '🗺️ RTL 实时地图',
    navLocators: '📡 定位基站',
    navTags: '🏷️ 发光标签',
    engineConnected: 'ENGINE CONNECTED',
    engineSub: 'LOCAL · PORT 7201',
    soundOn: '音频: 开',
    soundOff: '音频: 关',
    themeLight: '浅色',
    themeDark: '深色',
    allStop: '⏹ 全部熄灭',
    searchPlaceholder: '搜索商品名、SKU、10位ID、货架...',
    tabAllTags: '全部标签',
    tabFlashingTags: '正在点灯',
    btnAddDevice: '放置新标签',
    btnMapSettings: '图纸',
    btnDataSettings: '备份',
    registeredTags: 'REGISTERED TAGS',
    onlineLocators: 'ONLINE LOCATORS (GA25)',
    activeFlashing: 'ACTIVE FLASHING',
    aoaAccuracy: 'AoA ACCURACY',
    findingPrefix: '正在寻物',
    stopFinding: '停止寻物',
    noItemsFound: '未找到匹配的商品或标签',
    btnFlash: '💡 发光寻物',
    btnStopFlash: '⏹ 消灯',
    btnEdit: '✎ 编辑',
    btnDelete: '✕ 删除',
    modalTagTitle: '标签位置配置 · TAG PROPERTIES',
    modalTagEditTitle: '编辑标签配置 · EDIT TAG',
    labelProductName: '商品名称 *',
    labelSku: 'SKU / 条形码',
    labelBeaconId: '标签ID (10位数字编号) *',
    labelShelf: '区域 / 货架号',
    labelLedColor: 'LED 点灯发光颜色',
    labelCoords: '地图相对坐标 (X%, Y%)',
    dragHint: '直接在地图上拖拽标签图钉即可微调',
    btnCancel: '取消',
    btnSave: '保存配置',
    btnClose: '关闭',
    toastFound: '已锁定并点亮位置',
    toastStopped: '已停止点灯',
    toastAllStopped: '所有标签已停止点灯',
    toastPositionUpdated: '已更新图钉放置坐标',
    toastSaved: '标签配置已保存',
    toastDeleted: '已从地图中移除标签',
    toastLocatorsToggleOn: '已开启基站覆盖波束范围',
    toastLocatorsToggleOff: '已隐藏基站覆盖波束范围',
    toastGridOn: '网格已显示',
    toastGridOff: '网格已隐藏',
    toastLabelsOn: '标签名称已显示',
    toastLabelsOff: '标签名称已隐藏',
    toastThemeDark: '已切换至深色模式 (Night Mode)',
    toastThemeLight: '已切换至浅色模式 (Light Mode)',
    deleteConfirm: '确定要删除该标签吗？'
  },
  'ja': {
    title: 'ORBIT - 屋内高精度測位＆タグ発光探索コンソール',
    navRtl: '🗺️ RTL リアルタイム測位',
    navLocators: '📡 ロケーター基盤',
    navTags: '🏷️ 発光タグ',
    engineConnected: 'ENGINE CONNECTED',
    engineSub: 'LOCAL · PORT 7201',
    soundOn: '音声: ON',
    soundOff: '音声: OFF',
    themeLight: 'ライト',
    themeDark: 'ダーク',
    allStop: '⏹ すべて消灯',
    searchPlaceholder: '商品名、SKU、10桁ID、棚番号で検索...',
    tabAllTags: 'すべてのタグ',
    tabFlashingTags: '点滅中',
    btnAddDevice: '新規タグ配置',
    btnMapSettings: '図面',
    btnDataSettings: 'バックアップ',
    registeredTags: '登録タグ総数',
    onlineLocators: '稼働中ロケーター (GA25)',
    activeFlashing: '現在発光中',
    aoaAccuracy: 'AoA測位精度',
    findingPrefix: '探索中',
    stopFinding: '探索停止',
    noItemsFound: '該当する商品またはタグがありません',
    btnFlash: '💡 発光探索',
    btnStopFlash: '⏹ 消灯',
    btnEdit: '✎ 編集',
    btnDelete: '✕ 削除',
    modalTagTitle: 'タグ位置設定 · TAG PROPERTIES',
    modalTagEditTitle: 'タグ情報編集 · EDIT TAG',
    labelProductName: '商品名 *',
    labelSku: 'SKU / バーコード',
    labelBeaconId: 'タグID (10桁数字) *',
    labelShelf: '保管エリア / 棚番号',
    labelLedColor: 'LED発光カラー',
    labelCoords: 'マップ座標 (X%, Y%)',
    dragHint: 'マップ上のピンを直接ドラッグして微調整可能',
    btnCancel: 'キャンセル',
    btnSave: '設定を保存',
    btnClose: '閉じる',
    toastFound: '位置を捕捉し発光を開始しました',
    toastStopped: '消灯しました',
    toastAllStopped: 'すべての発光を停止しました',
    toastPositionUpdated: 'タグの配置座標を更新しました',
    toastSaved: 'タグ情報を保存しました',
    toastDeleted: 'タグを削除しました',
    toastLocatorsToggleOn: 'ロケーターのカバレッジを表示しました',
    toastLocatorsToggleOff: 'ロケーターのカバレッジを非表示にしました',
    toastGridOn: 'グリッドを表示しました',
    toastGridOff: 'グリッドを非表示にしました',
    toastLabelsOn: 'ラベルを表示しました',
    toastLabelsOff: 'ラベルを非表示にしました',
    toastThemeDark: 'ダークモードに切り替えました',
    toastThemeLight: 'ライトモードに切り替えました',
    deleteConfirm: 'このタグをマップから削除しますか？'
  },
  'en': {
    title: 'ORBIT - Indoor High-Precision AoA RTL & Tag Finder',
    navRtl: '🗺️ RTL Map',
    navLocators: '📡 Locators',
    navTags: '🏷️ Tags',
    engineConnected: 'ENGINE CONNECTED',
    engineSub: 'LOCAL · PORT 7201',
    soundOn: 'Audio: ON',
    soundOff: 'Audio: OFF',
    themeLight: 'Light',
    themeDark: 'Dark',
    allStop: '⏹ All Extinguish',
    searchPlaceholder: 'Search product, SKU, 10-digit ID, shelf...',
    tabAllTags: 'All Tags',
    tabFlashingTags: 'Flashing',
    btnAddDevice: 'Place New Tag',
    btnMapSettings: 'Floor Plan',
    btnDataSettings: 'Backup',
    registeredTags: 'REGISTERED TAGS',
    onlineLocators: 'ONLINE LOCATORS (GA25)',
    activeFlashing: 'ACTIVE FLASHING',
    aoaAccuracy: 'AoA ACCURACY',
    findingPrefix: 'Locating',
    stopFinding: 'Stop Finding',
    noItemsFound: 'No matching items or tags found',
    btnFlash: '💡 Find & Light',
    btnStopFlash: '⏹ Extinguish',
    btnEdit: '✎ Edit',
    btnDelete: '✕ Delete',
    modalTagTitle: 'Tag Properties · CONFIGURATION',
    modalTagEditTitle: 'Edit Tag · CONFIGURATION',
    labelProductName: 'Product Name *',
    labelSku: 'SKU / Barcode',
    labelBeaconId: 'Tag ID (10-digit) *',
    labelShelf: 'Zone / Shelf Location',
    labelLedColor: 'LED Flashing Color',
    labelCoords: 'Map Coordinates (X%, Y%)',
    dragHint: 'Drag marker on map to fine-tune physical position',
    btnCancel: 'Cancel',
    btnSave: 'Save Configuration',
    btnClose: 'Close',
    toastFound: 'Target located! Flashing initiated',
    toastStopped: 'Extinguished tag',
    toastAllStopped: 'All tags extinguished',
    toastPositionUpdated: 'Updated tag position',
    toastSaved: 'Tag configuration saved',
    toastDeleted: 'Removed tag from map',
    toastLocatorsToggleOn: 'Locator beam coverage visible',
    toastLocatorsToggleOff: 'Locator beam coverage hidden',
    toastGridOn: 'Grid visible',
    toastGridOff: 'Grid hidden',
    toastLabelsOn: 'Labels visible',
    toastLabelsOff: 'Labels hidden',
    toastThemeDark: 'Switched to Night Mode',
    toastThemeLight: 'Switched to Light Mode',
    deleteConfirm: 'Are you sure you want to delete this tag?'
  }
};

// ─────────────────────────────────────────────────────────────
// 3. Application State
// ─────────────────────────────────────────────────────────────

let currentLang = localStorage.getItem(STORAGE_KEY_LANG) || 'zh';
let isNightTheme = localStorage.getItem(STORAGE_KEY_THEME) !== 'light';

let locators = [...DEFAULT_LOCATORS];
let devices = [];
let activeFlashingIds = new Set();
let flashingTimers = {};
let audioBeepTimers = {};

let mapScale = 1.0;
let mapPanX = 0;
let mapPanY = 0;

let isPanning = false;
let panStartX = 0;
let panStartY = 0;

let isDraggingMarker = false;
let draggedDeviceId = null;

let isAddMode = false;
let currentFilterTab = 'all'; // 'all' or 'flashing'
let soundEnabled = true;
let showLabels = true;
let showGrid = true;
let showLocatorsCoverage = true;

let audioCtx = null;

// Helper to get localized string
function t(key) {
  const dict = I18N[currentLang] || I18N['zh'];
  return dict[key] || I18N['zh'][key] || key;
}

// ─────────────────────────────────────────────────────────────
// 4. Initialization (Lifecycle)
// ─────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initLanguage();
  loadDevices();
  loadCustomMap();
  initColorPicker();
  setupEventListeners();
  renderLocators();
  renderMapMarkers();
  renderDeviceList();
  updateTelemetry();
  fitMapToViewport();
});

function initTheme() {
  document.body.classList.toggle('orbit-night', isNightTheme);
  document.body.classList.toggle('orbit-light', !isNightTheme);
  const icon = document.getElementById('theme-icon');
  const label = document.getElementById('theme-label');
  if (icon && label) {
    icon.textContent = isNightTheme ? '☀️' : '🌙';
    label.textContent = isNightTheme ? t('themeLight') : t('themeDark');
  }
}

function initLanguage() {
  applyLanguage(currentLang);
}

function applyLanguage(lang) {
  currentLang = lang;
  localStorage.setItem(STORAGE_KEY_LANG, lang);
  document.title = t('title');

  // Update Top Nav
  const navRtl = document.getElementById('nav-tab-rtl');
  if (navRtl) navRtl.textContent = t('navRtl');

  const navCountLocators = document.getElementById('nav-count-locators');
  const navCountTags = document.getElementById('nav-count-tags');
  const locVal = navCountLocators ? navCountLocators.textContent : locators.length;
  const tagVal = navCountTags ? navCountTags.textContent : devices.length;

  const navLoc = document.getElementById('nav-tab-locators');
  if (navLoc) navLoc.innerHTML = `${t('navLocators')} (<span id="nav-count-locators">${locVal}</span>)`;

  const navTags = document.getElementById('nav-tab-tags');
  if (navTags) navTags.innerHTML = `${t('navTags')} (<span id="nav-count-tags">${tagVal}</span>)`;

  // Language button label
  const langLabel = document.getElementById('lang-label');
  if (langLabel) {
    langLabel.textContent = lang === 'zh' ? '中文' : (lang === 'ja' ? '日本語' : 'English');
  }

  // Theme button label
  const themeLabel = document.getElementById('theme-label');
  if (themeLabel) {
    themeLabel.textContent = isNightTheme ? t('themeLight') : t('themeDark');
  }

  // Sound button label
  const soundLabel = document.getElementById('sound-label');
  if (soundLabel) {
    soundLabel.textContent = soundEnabled ? t('soundOn') : t('soundOff');
  }

  // Search input placeholder
  const searchInput = document.getElementById('search-input');
  if (searchInput) searchInput.placeholder = t('searchPlaceholder');

  // Sidebar filter tabs
  const tabAll = document.getElementById('tab-all-devices');
  const tabFlashing = document.getElementById('tab-flashing-devices');
  if (tabAll) tabAll.innerHTML = `${t('tabAllTags')} (<span id="count-all">${devices.length}</span>)`;
  if (tabFlashing) tabFlashing.innerHTML = `${t('tabFlashingTags')} (<span id="count-flashing">${activeFlashingIds.size}</span>)`;

  // Sidebar actions
  const btnAdd = document.getElementById('btn-add-device');
  if (btnAdd) btnAdd.innerHTML = `<span>+</span> ${t('btnAddDevice')}`;
  const btnMap = document.getElementById('btn-map-settings');
  if (btnMap) btnMap.innerHTML = `🗺️ ${t('btnMapSettings')}`;
  const btnData = document.getElementById('btn-data-settings');
  if (btnData) btnData.innerHTML = `💾 ${t('btnDataSettings')}`;

  const btnAllStop = document.getElementById('btn-all-stop');
  if (btnAllStop) btnAllStop.textContent = t('allStop');

  // Re-render device list to update button labels
  renderDeviceList();
}

function toggleLanguage() {
  const nextLang = currentLang === 'zh' ? 'ja' : (currentLang === 'ja' ? 'en' : 'zh');
  applyLanguage(nextLang);
  showToast(`🌐 语言切换: ${nextLang === 'zh' ? '简体中文' : (nextLang === 'ja' ? '日本語' : 'English')}`);
}

function toggleTheme() {
  isNightTheme = !isNightTheme;
  document.body.classList.toggle('orbit-night', isNightTheme);
  document.body.classList.toggle('orbit-light', !isNightTheme);
  localStorage.setItem(STORAGE_KEY_THEME, isNightTheme ? 'night' : 'light');

  const icon = document.getElementById('theme-icon');
  const label = document.getElementById('theme-label');
  if (icon && label) {
    icon.textContent = isNightTheme ? '☀️' : '🌙';
    label.textContent = isNightTheme ? t('themeLight') : t('themeDark');
  }
  showToast(isNightTheme ? t('toastThemeDark') : t('toastThemeLight'));
}

function loadDevices() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_DEVICES);
    if (raw) {
      devices = JSON.parse(raw);
    } else {
      // Check legacy storage key
      const legacy = localStorage.getItem('beacon_finder_pc_devices');
      if (legacy) {
        devices = JSON.parse(legacy);
      } else {
        devices = JSON.parse(JSON.stringify(DEFAULT_DEVICES));
      }
      saveDevices();
    }
  } catch (e) {
    devices = JSON.parse(JSON.stringify(DEFAULT_DEVICES));
  }
}

function saveDevices() {
  try {
    localStorage.setItem(STORAGE_KEY_DEVICES, JSON.stringify(devices));
  } catch (e) {}
}

function loadCustomMap() {
  const customMapData = localStorage.getItem(STORAGE_KEY_MAP);
  const imgElem = document.getElementById('custom-map-img');
  const svgElem = document.getElementById('default-blueprint-svg');
  if (customMapData) {
    imgElem.src = customMapData;
    imgElem.style.display = 'block';
    svgElem.style.display = 'none';
  } else {
    imgElem.style.display = 'none';
    svgElem.style.display = 'block';
  }
}

// ─────────────────────────────────────────────────────────────
// 5. Map Pan & Zoom Engine (RTL Coordinate Projector)
// ─────────────────────────────────────────────────────────────

const viewport = document.getElementById('map-viewport');
const container = document.getElementById('map-container');
const surface = document.getElementById('map-surface');

function updateTransform(smooth = false) {
  if (smooth) {
    container.style.transition = 'transform 0.45s cubic-bezier(0.16, 1, 0.3, 1)';
    setTimeout(() => { container.style.transition = ''; }, 480);
  }
  container.style.transform = `translate(${mapPanX}px, ${mapPanY}px) scale(${mapScale})`;
}

function fitMapToViewport() {
  const vpRect = viewport.getBoundingClientRect();
  const scaleX = (vpRect.width - 50) / MAP_WIDTH;
  const scaleY = (vpRect.height - 50) / MAP_HEIGHT;
  mapScale = Math.min(scaleX, scaleY, 1.25);
  mapScale = Math.max(mapScale, 0.22);

  mapPanX = (vpRect.width - MAP_WIDTH * mapScale) / 2;
  mapPanY = (vpRect.height - MAP_HEIGHT * mapScale) / 2;
  updateTransform(true);
}

function resetZoom() {
  const vpRect = viewport.getBoundingClientRect();
  mapScale = 1.0;
  mapPanX = (vpRect.width - MAP_WIDTH) / 2;
  mapPanY = (vpRect.height - MAP_HEIGHT) / 2;
  updateTransform(true);
}

function zoomAtPoint(delta, clientX, clientY) {
  const newScale = Math.min(Math.max(mapScale * (delta > 0 ? 1.2 : 0.83), 0.22), 3.8);
  if (newScale === mapScale) return;

  const vpRect = viewport.getBoundingClientRect();
  const mouseX = clientX - vpRect.left;
  const mouseY = clientY - vpRect.top;

  mapPanX = mouseX - (mouseX - mapPanX) * (newScale / mapScale);
  mapPanY = mouseY - (mouseY - mapPanY) * (newScale / mapScale);
  mapScale = newScale;
  updateTransform();
}

function centerOnCoordinates(percentX, percentY, targetScale = 1.6) {
  const vpRect = viewport.getBoundingClientRect();
  const targetX = (percentX / 100) * MAP_WIDTH;
  const targetY = (percentY / 100) * MAP_HEIGHT;

  mapScale = targetScale;
  mapPanX = (vpRect.width / 2) - (targetX * mapScale);
  mapPanY = (vpRect.height / 2) - (targetY * mapScale);
  updateTransform(true);
}

// Convert percentage coordinates to real-world warehouse meters
function percentToMeters(percentX, percentY) {
  const mx = (percentX / 100) * WAREHOUSE_METERS_X;
  const my = (percentY / 100) * WAREHOUSE_METERS_Y;
  return {
    x: mx.toFixed(2),
    y: my.toFixed(2)
  };
}

// ─────────────────────────────────────────────────────────────
// 6. Base Stations (GA25 Locators Rendering)
// ─────────────────────────────────────────────────────────────

function renderLocators() {
  const layer = document.getElementById('locators-layer');
  if (!layer) return;
  layer.innerHTML = '';

  locators.forEach(loc => {
    const el = document.createElement('div');
    el.className = 'map-locator';
    el.dataset.id = loc.id;
    el.style.left = `${loc.x}%`;
    el.style.top = `${loc.y}%`;

    const meters = percentToMeters(loc.x, loc.y);

    el.innerHTML = `
      <div class="locator-coverage-ring" style="display: ${showLocatorsCoverage ? 'block' : 'none'}; width: ${loc.coverageRadius * 2}px; height: ${loc.coverageRadius * 2}px;"></div>
      <img src="assets/ga25.png" class="locator-icon" alt="${loc.name}">
      <div class="locator-label">${loc.name}</div>
    `;

    el.addEventListener('click', (e) => {
      e.stopPropagation();
      centerOnCoordinates(loc.x, loc.y, 1.8);
      showToast(`📡 [${loc.name}] ${loc.model} · IP: ${loc.ip} · 测角高度: ${loc.z}m · 坐标: (${meters.x}m, ${meters.y}m)`);
    });

    layer.appendChild(el);
  });

  const locCountBadge = document.getElementById('metric-locators-count');
  if (locCountBadge) locCountBadge.textContent = `${locators.length} / ${locators.length}`;
  const navLocCount = document.getElementById('nav-count-locators');
  if (navLocCount) navLocCount.textContent = locators.length;
}

// ─────────────────────────────────────────────────────────────
// 7. AoA Flashing & Finding Visualizer (Animation & Sound)
// ─────────────────────────────────────────────────────────────

function startFlashing(device) {
  if (!device) return;

  initAudio();
  activeFlashingIds.add(device.id);

  // Smooth cinematic glide zoom to device
  centerOnCoordinates(device.x, device.y, 1.65);

  // Update marker on map
  const markerElem = document.querySelector(`.map-marker[data-id="${device.id}"]`);
  if (markerElem) {
    markerElem.classList.add('flashing');
    renderMarkerCallout(markerElem, device);
  }

  // Dual-frequency AoA acoustic pulse (880Hz -> 1760Hz periodic locator chirp)
  if (soundEnabled && !audioBeepTimers[device.id]) {
    playLocatorBeep();
    audioBeepTimers[device.id] = setInterval(() => {
      if (activeFlashingIds.has(device.id) && soundEnabled) {
        playLocatorBeep();
      }
    }, 1200);
  }

  // 25-second auto timeout (hardware beacon power conservation simulation)
  if (flashingTimers[device.id]) clearTimeout(flashingTimers[device.id]);
  flashingTimers[device.id] = setTimeout(() => {
    stopFlashing(device.id);
  }, 25000);

  updateTelemetry();
  updateBanner(device);
  renderDeviceList();
  showToast(`💡 ${t('toastFound')}: 「${device.name}」 (${device.shelf || device.code})`);
}

function stopFlashing(deviceId) {
  activeFlashingIds.delete(deviceId);

  if (flashingTimers[deviceId]) {
    clearTimeout(flashingTimers[deviceId]);
    delete flashingTimers[deviceId];
  }
  if (audioBeepTimers[deviceId]) {
    clearInterval(audioBeepTimers[deviceId]);
    delete audioBeepTimers[deviceId];
  }

  const markerElem = document.querySelector(`.map-marker[data-id="${deviceId}"]`);
  if (markerElem) {
    markerElem.classList.remove('flashing');
    const callout = markerElem.querySelector('.marker-callout');
    if (callout) callout.remove();
  }

  updateTelemetry();
  updateBanner(null);
  renderDeviceList();
}

function stopAllFlashing() {
  const ids = Array.from(activeFlashingIds);
  ids.forEach(id => stopFlashing(id));
  showToast(t('toastAllStopped'));
}

function updateBanner(targetDevice) {
  const banner = document.getElementById('active-banner');
  const bannerText = document.getElementById('banner-text');
  const allStopBtn = document.getElementById('btn-all-stop');

  if (activeFlashingIds.size > 0) {
    if (allStopBtn) allStopBtn.style.display = 'inline-flex';
    if (banner) {
      banner.style.display = 'flex';
      if (targetDevice) {
        bannerText.textContent = `${t('findingPrefix')}: ${targetDevice.name} (ID: ${targetDevice.code} · ${targetDevice.shelf || 'AoA Locked'})`;
        document.getElementById('btn-banner-stop').onclick = () => stopFlashing(targetDevice.id);
      } else {
        bannerText.textContent = `${activeFlashingIds.size} ${t('tabFlashingTags')}`;
        document.getElementById('btn-banner-stop').onclick = stopAllFlashing;
      }
    }
  } else {
    if (allStopBtn) allStopBtn.style.display = 'none';
    if (banner) banner.style.display = 'none';
  }
}

function updateTelemetry() {
  const tagsCount = document.getElementById('metric-tags-count');
  const flashingCount = document.getElementById('metric-flashing-count');
  const navTagsCount = document.getElementById('nav-count-tags');
  const countAll = document.getElementById('count-all');
  const countFlashing = document.getElementById('count-flashing');

  if (tagsCount) tagsCount.textContent = devices.length;
  if (flashingCount) flashingCount.textContent = activeFlashingIds.size;
  if (navTagsCount) navTagsCount.textContent = devices.length;
  if (countAll) countAll.textContent = devices.length;
  if (countFlashing) countFlashing.textContent = activeFlashingIds.size;
}

// ─────────────────────────────────────────────────────────────
// 8. Acoustic Synthesizer (Web Audio API)
// ─────────────────────────────────────────────────────────────

function initAudio() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  if (audioCtx && audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
}

function playLocatorBeep() {
  if (!soundEnabled || !audioCtx) return;
  try {
    const now = audioCtx.currentTime;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    // 880Hz to 1760Hz dual-frequency chirp
    osc.frequency.setValueAtTime(880, now);
    osc.frequency.exponentialRampToValueAtTime(1760, now + 0.08);

    gain.gain.setValueAtTime(0.18, now);
    gain.gain.exponentialRampToValueAtTime(0.005, now + 0.12);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    osc.start(now);
    osc.stop(now + 0.12);
  } catch (e) {}
}

// ─────────────────────────────────────────────────────────────
// 9. Map Markers & Drag & Drop Handling
// ─────────────────────────────────────────────────────────────

function renderMapMarkers() {
  const layer = document.getElementById('markers-layer');
  if (!layer) return;
  layer.innerHTML = '';

  devices.forEach(dev => {
    const isFlashing = activeFlashingIds.has(dev.id);

    const marker = document.createElement('div');
    marker.className = `map-marker ${isFlashing ? 'flashing' : ''}`;
    marker.dataset.id = dev.id;
    marker.style.left = `${dev.x}%`;
    marker.style.top = `${dev.y}%`;
    marker.style.color = dev.color || '#10B981';

    marker.innerHTML = `
      <div class="marker-waves-container">
        <div class="radar-aura" style="color: ${dev.color}"></div>
        <div class="wave-ring" style="color: ${dev.color}"></div>
        <div class="wave-ring" style="color: ${dev.color}"></div>
        <div class="wave-ring" style="color: ${dev.color}"></div>
      </div>
      <div class="marker-puck" style="border-color: ${dev.color}">
        <div class="marker-led-core" style="background-color: ${dev.color}; color: ${dev.color};"></div>
      </div>
      <div class="marker-label" style="display: ${showLabels ? 'block' : 'none'};">
        ${dev.name}
      </div>
    `;

    // Click marker to toggle find & flash
    marker.addEventListener('click', (e) => {
      e.stopPropagation();
      if (isDraggingMarker) return;
      if (activeFlashingIds.has(dev.id)) {
        stopFlashing(dev.id);
      } else {
        startFlashing(dev);
      }
    });

    // Mouse drag to reposition marker
    marker.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      e.stopPropagation();
      startMarkerDrag(e, dev);
    });

    layer.appendChild(marker);

    // If currently flashing, restore HUD popup
    if (isFlashing) {
      renderMarkerCallout(marker, dev);
    }
  });
}

function renderMarkerCallout(markerElem, dev) {
  let callout = markerElem.querySelector('.marker-callout');
  if (!callout) {
    callout = document.createElement('div');
    callout.className = 'marker-callout';
    markerElem.appendChild(callout);
  }

  const meters = percentToMeters(dev.x, dev.y);

  callout.innerHTML = `
    <div class="callout-header">
      <div class="callout-title" style="color: ${dev.color}">
        <span>●</span> <span>${dev.name}</span>
      </div>
    </div>
    <div class="callout-row">
      <span>ID / SKU:</span>
      <span>${dev.code} · ${dev.sku || 'N/A'}</span>
    </div>
    <div class="callout-row">
      <span>${t('labelShelf')}:</span>
      <span>${dev.shelf || '未指定'}</span>
    </div>
    <div class="callout-row">
      <span>物理坐标:</span>
      <span>${meters.x}m, ${meters.y}m (${dev.x}%, ${dev.y}%)</span>
    </div>
    <button class="callout-stop-btn">${t('btnStopFlash')}</button>
  `;

  callout.querySelector('.callout-stop-btn').addEventListener('click', (e) => {
    e.stopPropagation();
    stopFlashing(dev.id);
  });
}

function startMarkerDrag(e, dev) {
  isDraggingMarker = true;
  draggedDeviceId = dev.id;

  const marker = document.querySelector(`.map-marker[data-id="${dev.id}"]`);
  if (marker) marker.classList.add('is-dragging');

  const onMouseMove = (moveEvent) => {
    if (!isDraggingMarker) return;
    const surfaceRect = surface.getBoundingClientRect();

    let newX = ((moveEvent.clientX - surfaceRect.left) / surfaceRect.width) * 100;
    let newY = ((moveEvent.clientY - surfaceRect.top) / surfaceRect.height) * 100;

    newX = Math.max(1.0, Math.min(99.0, newX));
    newY = Math.max(1.0, Math.min(99.0, newY));

    dev.x = Math.round(newX * 10) / 10;
    dev.y = Math.round(newY * 10) / 10;

    if (marker) {
      marker.style.left = `${dev.x}%`;
      marker.style.top = `${dev.y}%`;
      const callout = marker.querySelector('.marker-callout');
      if (callout) {
        const meters = percentToMeters(dev.x, dev.y);
        const coordRow = callout.querySelectorAll('.callout-row')[2];
        if (coordRow) {
          coordRow.children[1].textContent = `${meters.x}m, ${meters.y}m (${dev.x}%, ${dev.y}%)`;
        }
      }
    }
  };

  const onMouseUp = () => {
    isDraggingMarker = false;
    draggedDeviceId = null;
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup', onMouseUp);

    if (marker) marker.classList.remove('is-dragging');
    saveDevices();
    renderDeviceList();
    const meters = percentToMeters(dev.x, dev.y);
    showToast(`📍 ${t('toastPositionUpdated')}: 「${dev.name}」 (${meters.x}m, ${meters.y}m)`);
  };

  document.addEventListener('mousemove', onMouseMove);
  document.addEventListener('mouseup', onMouseUp);
}

// ─────────────────────────────────────────────────────────────
// 10. Sidebar Tag List & Search Engine
// ─────────────────────────────────────────────────────────────

function renderDeviceList() {
  const container = document.getElementById('device-list');
  const searchInput = document.getElementById('search-input');
  if (!container) return;

  const query = searchInput ? searchInput.value.trim().toLowerCase() : '';

  let filtered = devices.filter(dev => {
    if (currentFilterTab === 'flashing' && !activeFlashingIds.has(dev.id)) {
      return false;
    }
    if (!query) return true;
    const matchName = dev.name && dev.name.toLowerCase().includes(query);
    const matchSku = dev.sku && dev.sku.toLowerCase().includes(query);
    const matchCode = dev.code && dev.code.toLowerCase().includes(query);
    const matchShelf = dev.shelf && dev.shelf.toLowerCase().includes(query);
    return matchName || matchSku || matchCode || matchShelf;
  });

  container.innerHTML = '';

  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="empty-list" style="text-align: center; padding: 40px 10px; color: var(--orbit-muted);">
        <span style="font-size: 32px; display: block; margin-bottom: 8px;">🔍</span>
        <div>${t('noItemsFound')}</div>
      </div>
    `;
    return;
  }

  filtered.forEach(dev => {
    const isFlashing = activeFlashingIds.has(dev.id);
    const meters = percentToMeters(dev.x, dev.y);

    const card = document.createElement('div');
    card.className = `device-card ${isFlashing ? 'is-flashing' : ''}`;
    card.dataset.id = dev.id;

    card.innerHTML = `
      <div class="card-top">
        <div class="card-title-group">
          <img src="assets/tag-blue.png" class="card-tag-icon" alt="tag">
          <span class="card-title">${dev.name}</span>
        </div>
        <span class="card-led-indicator" style="background-color: ${dev.color}; color: ${dev.color};"></span>
      </div>
      <div class="card-details">
        <span class="pill-shelf">${dev.shelf || '未指定'}</span>
        <span class="card-coordinates">${meters.x}m, ${meters.y}m</span>
      </div>
      <div class="card-bottom">
        <span class="card-beacon-code">ID: ${dev.code}</span>
        <div class="card-buttons">
          <button class="btn btn-sm ${isFlashing ? 'btn-danger' : 'btn-primary'} btn-action-flash">
            ${isFlashing ? t('btnStopFlash') : t('btnFlash')}
          </button>
          <button class="btn btn-sm btn-outline btn-action-edit" title="${t('btnEdit')}">✎</button>
          <button class="btn btn-sm btn-outline btn-action-delete" title="${t('btnDelete')}" style="color: var(--orbit-red);">✕</button>
        </div>
      </div>
    `;

    // Click card to center and start flashing
    card.addEventListener('click', (e) => {
      if (e.target.closest('.card-buttons')) return;
      centerOnCoordinates(dev.x, dev.y, 1.65);
      startFlashing(dev);
    });

    // Find & Flash button
    card.querySelector('.btn-action-flash').addEventListener('click', (e) => {
      e.stopPropagation();
      if (activeFlashingIds.has(dev.id)) {
        stopFlashing(dev.id);
      } else {
        startFlashing(dev);
      }
    });

    // Edit button
    card.querySelector('.btn-action-edit').addEventListener('click', (e) => {
      e.stopPropagation();
      openDeviceModal(dev);
    });

    // Delete button
    card.querySelector('.btn-action-delete').addEventListener('click', (e) => {
      e.stopPropagation();
      if (confirm(`${t('deleteConfirm')}「${dev.name}」(ID: ${dev.code})`)) {
        devices = devices.filter(d => d.id !== dev.id);
        stopFlashing(dev.id);
        saveDevices();
        renderDeviceList();
        renderMapMarkers();
        updateTelemetry();
        showToast(t('toastDeleted'));
      }
    });

    container.appendChild(card);
  });
}

// ─────────────────────────────────────────────────────────────
// 11. Modal Dialogs & Property Forms
// ─────────────────────────────────────────────────────────────

function initColorPicker() {
  const container = document.getElementById('color-picker-container');
  if (!container) return;
  container.innerHTML = '';

  LED_COLORS.forEach(c => {
    const opt = document.createElement('div');
    opt.className = `color-option ${c.hex === '#10B981' ? 'selected' : ''}`;
    opt.dataset.hex = c.hex;
    opt.style.backgroundColor = c.hex;
    opt.style.color = c.hex;
    opt.title = `${currentLang === 'zh' ? c.nameZh : (currentLang === 'ja' ? c.nameJa : c.nameEn)} (${c.hex})`;

    opt.addEventListener('click', () => {
      container.querySelectorAll('.color-option').forEach(el => el.classList.remove('selected'));
      opt.classList.add('selected');
    });

    container.appendChild(opt);
  });
}

function openDeviceModal(existingDevice = null, defaultCoords = null) {
  const modal = document.getElementById('modal-device');
  const title = document.getElementById('modal-device-title');
  if (!modal) return;

  if (existingDevice) {
    title.textContent = t('modalTagEditTitle');
    document.getElementById('form-device-id').value = existingDevice.id;
    document.getElementById('form-product-name').value = existingDevice.name;
    document.getElementById('form-sku').value = existingDevice.sku || '';
    document.getElementById('form-beacon-code').value = existingDevice.code;
    document.getElementById('form-shelf').value = existingDevice.shelf || '';
    document.getElementById('form-device-x').value = existingDevice.x;
    document.getElementById('form-device-y').value = existingDevice.y;
    document.getElementById('label-device-x').textContent = `${existingDevice.x}%`;
    document.getElementById('label-device-y').textContent = `${existingDevice.y}%`;

    const colorOpts = document.querySelectorAll('#color-picker-container .color-option');
    colorOpts.forEach(opt => {
      opt.classList.toggle('selected', opt.dataset.hex.toLowerCase() === (existingDevice.color || '#10b981').toLowerCase());
    });
  } else {
    title.textContent = t('modalTagTitle');
    document.getElementById('form-device-id').value = '';
    document.getElementById('form-product-name').value = '';
    document.getElementById('form-sku').value = '';

    // Auto generate next sequential 10-digit Beacon ID
    const nextCode = '000000' + String(1000 + devices.length + 1).padStart(4, '0');
    document.getElementById('form-beacon-code').value = nextCode;
    document.getElementById('form-shelf').value = 'A棚-01';

    const coords = defaultCoords || { x: 50.0, y: 50.0 };
    document.getElementById('form-device-x').value = coords.x;
    document.getElementById('form-device-y').value = coords.y;
    document.getElementById('label-device-x').textContent = `${coords.x}%`;
    document.getElementById('label-device-y').textContent = `${coords.y}%`;
  }

  modal.classList.add('open');
}

function saveDeviceFromModal() {
  const name = document.getElementById('form-product-name').value.trim();
  if (!name) {
    alert(t('labelProductName'));
    return;
  }

  let code = document.getElementById('form-beacon-code').value.trim().replace(/[^0-9]/g, '');
  if (!code) {
    alert(t('labelBeaconId'));
    return;
  }
  code = code.padStart(10, '0').slice(-10);

  const selectedColorOpt = document.querySelector('#color-picker-container .color-option.selected');
  const color = selectedColorOpt ? selectedColorOpt.dataset.hex : '#10B981';

  const id = document.getElementById('form-device-id').value;
  const sku = document.getElementById('form-sku').value.trim();
  const shelf = document.getElementById('form-shelf').value.trim();
  const x = parseFloat(document.getElementById('form-device-x').value) || 50.0;
  const y = parseFloat(document.getElementById('form-device-y').value) || 50.0;

  if (id) {
    const dev = devices.find(d => d.id === id);
    if (dev) {
      dev.name = name;
      dev.sku = sku;
      dev.code = code;
      dev.shelf = shelf;
      dev.color = color;
      dev.x = x;
      dev.y = y;
    }
  } else {
    const newDev = {
      id: 'dev_' + Date.now(),
      name,
      sku,
      code,
      shelf,
      color,
      x,
      y
    };
    devices.push(newDev);
  }

  saveDevices();
  renderDeviceList();
  renderMapMarkers();
  updateTelemetry();
  closeAllModals();
  showToast(`${t('toastSaved')}: 「${name}」`);
}

function closeAllModals() {
  document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('open'));
}

// ─────────────────────────────────────────────────────────────
// 12. Event Listeners Setup
// ─────────────────────────────────────────────────────────────

function setupEventListeners() {
  // Navigation Tabs
  const navRtl = document.getElementById('nav-tab-rtl');
  const navLoc = document.getElementById('nav-tab-locators');
  const navTags = document.getElementById('nav-tab-tags');

  if (navRtl) {
    navRtl.addEventListener('click', () => {
      navRtl.classList.add('active');
      navLoc.classList.remove('active');
      navTags.classList.remove('active');
      fitMapToViewport();
    });
  }

  if (navLoc) {
    navLoc.addEventListener('click', () => {
      navLoc.classList.add('active');
      navRtl.classList.remove('active');
      navTags.classList.remove('active');
      showLocatorsCoverage = true;
      renderLocators();
      showToast(`📡 已显示所有 GA25 定位基站覆盖范围 (${locators.length}台在线)`);
    });
  }

  if (navTags) {
    navTags.addEventListener('click', () => {
      navTags.classList.add('active');
      navRtl.classList.remove('active');
      navLoc.classList.remove('active');
      currentFilterTab = 'all';
      document.getElementById('tab-all-devices').classList.add('active');
      document.getElementById('tab-flashing-devices').classList.remove('active');
      renderDeviceList();
      const searchBox = document.getElementById('search-input');
      if (searchBox) searchBox.focus();
    });
  }

  // System Live Pill
  const livePill = document.getElementById('system-live-pill');
  if (livePill) {
    livePill.addEventListener('click', () => {
      showToast('🟢 ORBIT CLE Engine v2.11.9 运行正常 (WebSocket: 7201 Connected)');
    });
  }

  // Language & Theme Toggle Buttons
  const langBtn = document.getElementById('btn-lang-toggle');
  if (langBtn) langBtn.addEventListener('click', toggleLanguage);

  const themeBtn = document.getElementById('btn-theme-toggle');
  if (themeBtn) themeBtn.addEventListener('click', toggleTheme);

  // Sound Toggle Button
  const soundBtn = document.getElementById('btn-sound-toggle');
  if (soundBtn) {
    soundBtn.addEventListener('click', () => {
      soundEnabled = !soundEnabled;
      const soundIcon = document.getElementById('sound-icon');
      const soundLabel = document.getElementById('sound-label');
      if (soundIcon) soundIcon.textContent = soundEnabled ? '🔔' : '🔕';
      if (soundLabel) soundLabel.textContent = soundEnabled ? t('soundOn') : t('soundOff');
      showToast(soundEnabled ? '🔔 寻物蜂鸣声已开启' : '🔕 寻物蜂鸣声已静音');
    });
  }

  // Stop All Flashing Button
  const allStopBtn = document.getElementById('btn-all-stop');
  if (allStopBtn) allStopBtn.addEventListener('click', stopAllFlashing);

  // Map Pan (Mouse Drag)
  viewport.addEventListener('mousedown', (e) => {
    if (e.button !== 0 || isDraggingMarker) return;
    if (e.target.closest('.map-marker') || e.target.closest('.map-locator') ||
        e.target.closest('.map-controls-floating') || e.target.closest('.active-finder-banner') ||
        e.target.closest('.telemetry-strip')) {
      return;
    }

    if (isAddMode) {
      const surfaceRect = surface.getBoundingClientRect();
      const clickX = ((e.clientX - surfaceRect.left) / surfaceRect.width) * 100;
      const clickY = ((e.clientY - surfaceRect.top) / surfaceRect.height) * 100;
      isAddMode = false;
      container.classList.remove('add-mode');
      openDeviceModal(null, {
        x: Math.round(Math.max(1, Math.min(99, clickX)) * 10) / 10,
        y: Math.round(Math.max(1, Math.min(99, clickY)) * 10) / 10
      });
      return;
    }

    isPanning = true;
    container.classList.add('is-panning');
    panStartX = e.clientX - mapPanX;
    panStartY = e.clientY - mapPanY;
  });

  window.addEventListener('mousemove', (e) => {
    if (!isPanning) return;
    mapPanX = e.clientX - panStartX;
    mapPanY = e.clientY - panStartY;
    updateTransform();
  });

  window.addEventListener('mouseup', () => {
    if (isPanning) {
      isPanning = false;
      container.classList.remove('is-panning');
    }
  });

  // Mouse Wheel Zoom
  viewport.addEventListener('wheel', (e) => {
    e.preventDefault();
    zoomAtPoint(e.deltaY < 0 ? 1 : -1, e.clientX, e.clientY);
  }, { passive: false });

  // Map Controls Buttons
  document.getElementById('btn-zoom-in').addEventListener('click', () => {
    const vpRect = viewport.getBoundingClientRect();
    zoomAtPoint(1, vpRect.left + vpRect.width / 2, vpRect.top + vpRect.height / 2);
  });
  document.getElementById('btn-zoom-out').addEventListener('click', () => {
    const vpRect = viewport.getBoundingClientRect();
    zoomAtPoint(-1, vpRect.left + vpRect.width / 2, vpRect.top + vpRect.height / 2);
  });
  document.getElementById('btn-zoom-fit').addEventListener('click', fitMapToViewport);
  document.getElementById('btn-zoom-reset').addEventListener('click', resetZoom);

  // Toggle Grid, Labels, Locators
  document.getElementById('btn-toggle-grid').addEventListener('click', () => {
    showGrid = !showGrid;
    const gridOverlay = document.getElementById('grid-overlay');
    if (gridOverlay) gridOverlay.style.display = showGrid ? 'block' : 'none';
    showToast(showGrid ? t('toastGridOn') : t('toastGridOff'));
  });

  document.getElementById('btn-toggle-labels').addEventListener('click', () => {
    showLabels = !showLabels;
    document.querySelectorAll('.marker-label').forEach(el => {
      el.style.display = showLabels ? 'block' : 'none';
    });
    showToast(showLabels ? t('toastLabelsOn') : t('toastLabelsOff'));
  });

  document.getElementById('btn-toggle-locators').addEventListener('click', () => {
    showLocatorsCoverage = !showLocatorsCoverage;
    document.querySelectorAll('.locator-coverage-ring').forEach(el => {
      el.style.display = showLocatorsCoverage ? 'block' : 'none';
    });
    showToast(showLocatorsCoverage ? t('toastLocatorsToggleOn') : t('toastLocatorsToggleOff'));
  });

  // Search Input
  const searchInput = document.getElementById('search-input');
  const clearBtn = document.getElementById('btn-search-clear');

  searchInput.addEventListener('input', () => {
    clearBtn.style.display = searchInput.value ? 'block' : 'none';
    renderDeviceList();
  });

  searchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const q = searchInput.value.trim().toLowerCase();
      if (!q) return;
      const match = devices.find(d =>
        (d.name && d.name.toLowerCase().includes(q)) ||
        (d.code && d.code.includes(q)) ||
        (d.sku && d.sku.toLowerCase().includes(q)) ||
        (d.shelf && d.shelf.toLowerCase().includes(q))
      );
      if (match) {
        startFlashing(match);
      } else {
        showToast(`🔍 ${t('noItemsFound')}: 「${searchInput.value}」`);
      }
    }
  });

  clearBtn.addEventListener('click', () => {
    searchInput.value = '';
    clearBtn.style.display = 'none';
    renderDeviceList();
  });

  // Sidebar Filter Sub-tabs
  const tabAll = document.getElementById('tab-all-devices');
  const tabFlashing = document.getElementById('tab-flashing-devices');

  if (tabAll) {
    tabAll.addEventListener('click', () => {
      currentFilterTab = 'all';
      tabAll.classList.add('active');
      tabFlashing.classList.remove('active');
      renderDeviceList();
    });
  }

  if (tabFlashing) {
    tabFlashing.addEventListener('click', () => {
      currentFilterTab = 'flashing';
      tabFlashing.classList.add('active');
      tabAll.classList.remove('active');
      renderDeviceList();
    });
  }

  // Add Device Button (Click map to place)
  document.getElementById('btn-add-device').addEventListener('click', () => {
    isAddMode = true;
    container.classList.add('add-mode');
    showToast('🎯 请在地图上点击欲放置标签的位置');
  });

  // Floor Plan CAD Settings Modal
  document.getElementById('btn-map-settings').addEventListener('click', () => {
    document.getElementById('modal-map').classList.add('open');
  });

  // Upload Custom Floor Plan Image
  document.getElementById('input-map-file').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (loadEvent) => {
      const dataUrl = loadEvent.target.result;
      try {
        localStorage.setItem(STORAGE_KEY_MAP, dataUrl);
      } catch (err) {
        alert('图片数据过大，无法保存至浏览器本地。请压缩图片尺寸后重试。');
        return;
      }
      loadCustomMap();
      closeAllModals();
      showToast('🗺️ 自定义底图加载成功');
    };
    reader.readAsDataURL(file);
  });

  // Reset Default Blueprint
  document.getElementById('btn-reset-default-map').addEventListener('click', () => {
    localStorage.removeItem(STORAGE_KEY_MAP);
    loadCustomMap();
    closeAllModals();
    showToast('↺ 已恢复默认高精度工业仓库图纸');
  });

  // Data Settings Modal
  document.getElementById('btn-data-settings').addEventListener('click', () => {
    document.getElementById('modal-data').classList.add('open');
  });

  // Export JSON Backup
  document.getElementById('btn-export-json').addEventListener('click', () => {
    const backupData = {
      version: '2.11.9',
      system: 'ORBIT_AOA_CLE',
      exportedAt: new Date().toISOString(),
      locators,
      devices
    };
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(backupData, null, 2));
    const dlAnchor = document.createElement('a');
    dlAnchor.setAttribute('href', dataStr);
    dlAnchor.setAttribute('download', `orbit-aoa-backup-${new Date().toISOString().slice(0, 10)}.json`);
    document.body.appendChild(dlAnchor);
    dlAnchor.click();
    dlAnchor.remove();
    showToast('📥 备份数据文件已导出');
  });

  // Import JSON Backup
  document.getElementById('input-import-json').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (loadEvent) => {
      try {
        const parsed = JSON.parse(loadEvent.target.result);
        const importedDevices = Array.isArray(parsed) ? parsed : (parsed.devices || []);
        if (Array.isArray(importedDevices) && importedDevices.length > 0) {
          devices = importedDevices;
          if (parsed.locators && Array.isArray(parsed.locators)) {
            locators = parsed.locators;
          }
          saveDevices();
          renderLocators();
          renderDeviceList();
          renderMapMarkers();
          updateTelemetry();
          closeAllModals();
          showToast(`📥 成功恢复 ${devices.length} 个标签与 ${locators.length} 个基站数据`);
        } else {
          alert('导入失败：未找到有效的标签配置数据');
        }
      } catch (err) {
        alert('JSON 文件格式解析失败');
      }
    };
    reader.readAsText(file);
  });

  // Reset Demo Data
  document.getElementById('btn-reset-demo').addEventListener('click', () => {
    if (confirm('确定要清除所有修改并恢复默认工业演示数据吗？')) {
      devices = JSON.parse(JSON.stringify(DEFAULT_DEVICES));
      locators = JSON.parse(JSON.stringify(DEFAULT_LOCATORS));
      saveDevices();
      stopAllFlashing();
      renderLocators();
      renderDeviceList();
      renderMapMarkers();
      updateTelemetry();
      closeAllModals();
      showToast('↺ 已重置为初始演示数据');
    }
  });

  // Modal Close Buttons
  document.querySelectorAll('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => {
      const modalId = btn.getAttribute('data-close');
      document.getElementById(modalId).classList.remove('open');
    });
  });

  // Save Device Button
  document.getElementById('btn-save-device').addEventListener('click', saveDeviceFromModal);

  // Escape Key to Close Modals / Exit Add Mode
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeAllModals();
      if (isAddMode) {
        isAddMode = false;
        container.classList.remove('add-mode');
      }
    }
  });
}

// ─────────────────────────────────────────────────────────────
// 13. Toast Notification Component
// ─────────────────────────────────────────────────────────────

function showToast(message) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(8px)';
    toast.style.transition = 'opacity 0.25s ease, transform 0.25s ease';
    setTimeout(() => toast.remove(), 260);
  }, 3000);
}
