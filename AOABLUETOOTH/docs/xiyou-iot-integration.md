# 西柚数据接入说明（IoT 平台）

本文只说明可沿用代码、MQTT 数据、聚合状态接口及其部署方式。

ß## 1. 可以沿用的代码

以下代码都在原前端 `xiyou_web_code` 中，IoT 平台可按所用技术栈直接复用或参考。

| 功能 | 文件与行号 | 可沿用内容 |
|---|---|---|
| 登录并获取 MQTT 参数 | `xiyou_web_code/src/api/path/login.js:14-26` | Token 接口、当前用户及 MQTT 参数接口 |
| REST 请求鉴权 | `xiyou_web_code/src/api/http/tools.js:62-68` | 在 `Authorization` 请求头中携带 Token |
| REST 响应处理 | `xiyou_web_code/src/api/http/axios.js:73-93` | 判断 `code` 并取出 `detail` |
| MQTT 客户端初始化 | `xiyou_web_code/src/components/SlL7/mixins/mqtt.js:1-31` | MQTT.js 引入、QoS 和连接状态 |
| MQTT 连接及 JSON 解析 | `xiyou_web_code/src/components/SlL7/mixins/mqtt.js:45-83` | 使用返回的账号连接并执行 `JSON.parse()` |
| 按地图订阅 | `xiyou_web_code/src/components/SlL7/mixins/mqtt.js:111-132` | 拼接 Topic 并订阅 |
| 基站查询 | `xiyou_web_code/src/api/path/base-station.js:10-20` | 基站分页接口封装 |
| 信标查询和配置 | `xiyou_web_code/src/api/path/beacon-manage.js:16-73` | 查询、新增、编辑、删除、录入和绑定 |
| 网关状态 | `xiyou_web_code/src/api/path/service-set.js:13-36` | 查询网关和修改备注 |
| 定位对象 | `xiyou_web_code/src/api/path/object-manage.js:13-46` | 查询、编辑、绑定地图和删除 |
| CPA 配置 | `xiyou_web_code/src/api/path/floor-set.js:13-38` | 上传 CPA、查询结果和删除 CPA |
| 地图坐标转换 | `xiyou_web_code/src/components/SlL7/mixins/l7.js:13-55` | CLE 坐标与前端地图坐标互转 |

新增的聚合状态接口代码：

- `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/controller/IotController.java:1-26`
- `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/OverviewService.java:37-46`

MQTT 数据的后端生成代码可参考：

- `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/DataProcessService.java:161-168`
- `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/MQTTService.java:186-198`
- `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/BeaconDTO.java:18-68`

## 2. 取得 MQTT 连接信息

西柚云端 REST 地址：

```text
http://192.168.0.212:8001
```

### 2.1 登录取得 Token

```http
POST http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/token/request
Content-Type: application/json

{
  "username": "<用户名>",
  "password": "<密码>"
}
```

成功响应中的 `detail` 是 Token：

```json
{
  "code": "0000",
  "msg": "...",
  "detail": "<token>"
}
```

### 2.2 获取 MQTT 地址、账号和 Topic 前缀（具体使用实例看5.1，此处仅为介绍）

```http
GET http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/user/loginer
Authorization: <token>
```

响应格式：

```json
{
  "code": "0000",
  "msg": "...",
  "detail": {
    "userId": 1,
    "userName": "admin",
    "mqttUrl": "<ws-or-wss-url>",
    "mqttUsername": "<mqtt-user>",
    "mqttPassword": "<mqtt-password>",
    "mqttTopicPrefix": "/ifengniao/cloud/mqtt/xiyou/page/beacons/bymap/"
  }
}
```

连接时以接口实际返回的 `mqttUrl`、`mqttUsername`、`mqttPassword` 为准。

当前可达的 MQTT 入口：

| 客户端 | 地址 |
|---|---|
| 浏览器 WebSocket | `ws://192.168.0.212:8083/mqtt` |
| 浏览器加密 WebSocket | `wss://192.168.0.212:8084/mqtt` |
| IoT 后台 MQTT TCP | `mqtt://192.168.0.212:1883`；部分客户端写作 `tcp://192.168.0.212:1883` |

使用 `wss` 时，证书需要与访问地址匹配。`192.168.0.212` 是局域网地址。

## 3. MQTT 订阅地址

订阅单张地图：

```text
/ifengniao/cloud/mqtt/xiyou/page/beacons/bymap/{mapId}
```

订阅全部地图：

```text
/ifengniao/cloud/mqtt/xiyou/page/beacons/bymap/+
```

当前 QoS 为 `0`。

最小订阅代码可直接参考原前端：

```javascript
const client = mqtt.connect(mqttUrl, {
  username: mqttUsername,
  password: mqttPassword
});

client.subscribe('/ifengniao/cloud/mqtt/xiyou/page/beacons/bymap/+', { qos: 0 });

client.on('message', (topic, message) => {
  const data = JSON.parse(message.toString());
  console.log(topic, data);
});
```

## 4. MQTT 接收到的 JSON

MQTT Payload 是普通 UTF-8 JSON，不需要进行 Base64 或 GZIP 解压：

```json
{
  "type": "DEVICE_INFO",
  "data": [
    {
      "beaconId": 12,
      "beaconMac": "aabbcc112233",
      "beaconUniqueId": "aabbcc112233",
      "beaconX": "12.35",
      "beaconY": "7.89",
      "beaconZ": "1.20",
      "beaconLastTime": 1721000000100,
      "beaconType": 1,
      "beaconMapId": "cle-map-001",
      "beaconLocationObject": {
        "locationObjectId": 8,
        "locationObjectName": "员工01",
        "locationObjectType": 1,
        "locationObjectImgFileName": "person.png",
        "locationObjectImgViewUrl": "http://192.168.0.212/upload/locationobject/img/8.png",
        "locationObjectCreateTime": 1720000000000
      }
    }
  ],
  "version": ""
}
```

字段说明：

| 字段 | 含义 |
|---|---|
| `type` | 消息类型，实时定位为 `DEVICE_INFO` |
| `data` | 本次更新的信标数组 |
| `beaconId` | 西柚数据库中的信标 ID |
| `beaconMac` | 信标 MAC |
| `beaconX/Y/Z` | CLE 计算后的坐标 |
| `beaconLastTime` | 最后数据时间，Unix 毫秒时间戳 |
| `beaconType` | 信标类型 |
| `beaconMapId` | CLE/CPA 地图 ID |
| `beaconLocationObject` | 信标绑定的人员或物品 |

该 Topic 只发送已经绑定定位对象并匹配到地图的信标。它不包含完整在线状态、电量、RSSI、SOS 和最近基站信息，也没有现成的基站实时 Topic。

## 5. 一个接口获取全部设备状态

请求：

```http
GET http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/iot/status
Authorization: Bearer <JWT>
```

该接口仍需要西柚 Token。它沿用现有鉴权，不需要增加新的 IoT 账号体系；取得 Token 的方式见第 2.1 节。

### 5.1 尚未构建时从浏览器取得 Token （网页192.168.0.212用户名登录密码为admin/admin）

Token 可以在新接口构建、部署之前取得，但 `/iot/status` 必须部署新 JAR 后才能访问。

1. 在浏览器打开 `http://192.168.0.212` 使用admin/admin登录系统。
2. 按 `F12` 打开开发者工具。
3. 依次选择“应用”→“存储”→“Cookie”→`http://192.168.0.212`。
4. 找到 `FN_indoor_location_TOKEN`。
5. Cookie 值格式为 `Bearer%20eyJ...`；复制其中 `eyJ` 开头的 JWT 部分。

部署新 JAR 后，按照操作系统选择下面的命令。所有示例都只查看当前需要的基站在线状态和位置。

#### macOS / Linux

需要先安装 `jq`。macOS 可执行 `brew install jq`；Debian/Ubuntu 可执行 `sudo apt install jq`。

```bash
XIYOU_TOKEN='<粘贴 eyJ 开头的 JWT>'

curl -sS \
  -H "Authorization: Bearer $XIYOU_TOKEN" \
  'http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/iot/status' |
jq '{
  code,
  baseStations: [
    .detail.baseStations[] | {
      id: .baseStationId,
      mac: .baseStationMac,
      name: .baseStationName,
      ip: .baseStationIp,
      online: .baseStationOnline,
      lastTime: .baseStationLastTime,
      x: .baseStationX,
      y: .baseStationY,
      z: .baseStationZ
    }
  ]
}'
```

#### Windows PowerShell

PowerShell 版本不需要安装 `jq`。请在 PowerShell 中执行，不要在 CMD 中执行：

```powershell
$XiyouToken = '<粘贴 eyJ 开头的 JWT>'

$Response = Invoke-RestMethod `
  -Method Get `
  -Uri 'http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/iot/status' `
  -Headers @{ Authorization = "Bearer $XiyouToken" }

Write-Host "接口状态码：$($Response.code)"

$Response.detail.baseStations |
  Select-Object `
    baseStationId,
    baseStationMac,
    baseStationName,
    baseStationIp,
    baseStationOnline,
    baseStationLastTime,
    baseStationX,
    baseStationY,
    baseStationZ |
  Format-Table -AutoSize
```

#### Windows CMD

Windows 10/11 的 CMD 通常自带 `curl.exe`，下面的命令会输出完整原始 JSON：

```bat
set "XIYOU_TOKEN=<粘贴 eyJ 开头的 JWT>"

curl.exe -sS -H "Authorization: Bearer %XIYOU_TOKEN%" "http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/iot/status"
```

如果 CMD 提示找不到 `curl.exe`，直接使用上面的 PowerShell 版本。返回 `code` 为 `0000` 且 `baseStationOnline` 为 `true`，表示接口正常并且基站在线；`baseStationX`、`baseStationY`、`baseStationZ` 是基站安装坐标。

### 5.2 IoT 平台自动登录取得新 Token

Token 不是固定值：每次登录会重新生成，最长有效期为 7 天；连续 30 分钟没有请求也会失效。每次成功请求会自动刷新 30 分钟无操作期限。

IoT 平台不要保存某一个固定 Token，而应保存专用西柚账号和密码，启动时自动登录：

```bash
XIYOU_USER='iot_service'
XIYOU_PASSWORD='<西柚登录密码>'

XIYOU_AUTH=$(curl -sS -X POST \
  'http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/token/request' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$XIYOU_USER\",\"password\":\"$XIYOU_PASSWORD\"}" |
  jq -r '.detail')

curl -sS \
  -H "Authorization: $XIYOU_AUTH" \
  'http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/iot/status' | jq .
```

登录接口返回的 `detail` 已经包含 `Bearer ` 前缀。IoT 平台在启动时登录一次；请求聚合接口遇到 `401` 或 Token 失效时，重新登录并重试一次即可。

### 5.3 聚合响应格式

完整响应结构：

```json
{
  "code": "0000",
  "msg": "...",
  "errorDetail": null,
  "detail": {
    "gateways": [],
    "baseStations": [],
    "beacons": []
  }
}
```

三个数组一次返回数据库中的全部当前记录，不分页。

基站 `detail.baseStations[]` 格式：

```json
{
  "baseStationId": 10,
  "baseStationMac": "aabbccddeeff",
  "baseStationIp": "192.168.1.20",
  "baseStationElevation": "30.0",
  "baseStationAzimuth": "90.0",
  "baseStationRotation": "0.0",
  "baseStationErrorDegree": "2.5",
  "baseStationX": "2.4",
  "baseStationY": "5.8",
  "baseStationZ": "3.0",
  "baseStationOnline": true,
  "baseStationLastTime": 1721000000000,
  "baseStationName": "基站01",
  "baseStationType": "locator",
  "baseStationProduct": "AOA-Locator",
  "baseStationChannels": [37, 38, 39]
}
```

信标 `detail.beacons[]` 格式：

```json
{
  "beaconId": 12,
  "beaconMac": "aabbcc112233",
  "beaconUniqueId": "aabbcc112233",
  "beaconX": "12.35",
  "beaconY": "7.89",
  "beaconZ": "1.20",
  "beaconOnline": true,
  "beaconLastTime": 1721000000100,
  "beaconProduct": "tag-v1",
  "beaconType": 1,
  "beaconAllow": true,
  "beaconRemark": "员工标签",
  "beaconRssi": "-65.5",
  "beaconChannel": "37",
  "beaconFrequency": "2402",
  "beaconPowerType": "battery",
  "beaconSos": false,
  "beaconSosTime": -1,
  "beaconBattery": "86",
  "beaconLastGateway": "locator-01",
  "beaconNearestGateway": "locator-02",
  "beaconMapId": "cle-map-001",
  "beaconZoneId": "zone-01",
  "beaconZoneName": "仓库A区",
  "beaconLocationObject": {
    "locationObjectId": 8,
    "locationObjectName": "员工01",
    "locationObjectType": 1
  }
}
```

网关 `detail.gateways[]` 中与状态直接相关的字段：

```json
{
  "localServerId": 3,
  "localServerMac": "001122334455",
  "localServerIp": "192.168.1.10",
  "localServerOnline": true,
  "localServerCleStartup": true,
  "localServerCleActivation": true,
  "localServerLastTime": 1721000000000,
  "localServerCleLicenseExpireTime": 1750000000000,
  "localServerCleVersion": "2.9.9"
}
```

`localhost:44444` 和 `192.168.0.212:44444` 是 CLE 原始 HTTP 接口，不是上述解析后 MQTT 数据的订阅地址。

## 6. IoT 平台读取地图

IoT 平台不需要自己解析 CPA，也不需要访问虚拟机内部的 `localhost:44444`。CPA 仍由西柚设备上的 CLE 解析，解析后的地图图片、地图参数和基站信息会保存到西柚云端，IoT 平台只调用云端 REST 接口。

处理链路：

```text
上传 CPA
  → 西柚云端保存 CPA
  → 通过 MQTT 下发给西柚本地服务
  → 本地服务下载 CPA 并启动 CLE
  → CLE 解析地图、坐标和基站
  → 本地服务把地图图片和解析结果上传到云端
  → IoT 平台通过 REST 接口读取
```

### 6.1 获取地图列表

其他电脑应使用外部地址 `192.168.0.212`，推荐通过 80 端口的 Nginx 入口访问：

```http
GET http://192.168.0.212/ifengniao/cloud/server/xiyou/map/tree
Authorization: Bearer <JWT>
```

也可以直接访问云端后台的 8001 端口：

```text
http://192.168.0.212:8001/ifengniao/cloud/server/xiyou/map/tree
```

Token 的取得方式见第 5.1 和 5.2 节。响应中的最末级节点会提供 `mapId`：

```json
{
  "code": "0000",
  "msg": "成功",
  "errorDetail": null,
  "detail": [
    {
      "title": "建筑名称",
      "children": [
        {
          "title": "CPA项目名称",
          "children": [
            {
              "mapId": 1,
              "title": "楼层1"
            }
          ]
        }
      ]
    }
  ]
}
```

### 6.2 根据 mapId 获取地图图片和基站

```http
GET http://192.168.0.212/ifengniao/cloud/server/xiyou/map/id?mapId=1
Authorization: Bearer <JWT>
```

主要响应格式：

```json
{
  "code": "0000",
  "msg": "成功",
  "errorDetail": null,
  "detail": {
    "mapId": 1,
    "mapCpaId": "CPA中的地图ID",
    "mapCpaName": "楼层1",
    "mapModelType": "image",
    "mapWidth": "10.0",
    "mapHeight": "12.0",
    "mapWidthPixel": "1000",
    "mapHeightPixel": "1200",
    "mapMetersPerPixel": "0.01",
    "mapOriginPixelX": "500",
    "mapOriginPixelY": "600",
    "mapImgViewUrl": "http://192.168.0.212/upload/地图文件.png",
    "baseStationList": [
      {
        "baseStationId": 2,
        "baseStationMac": "3cfad3b0a111",
        "baseStationName": "基站01",
        "baseStationIp": "192.168.0.193",
        "baseStationOnline": true,
        "baseStationLastTime": 1784531256793,
        "baseStationX": "2.92",
        "baseStationY": "8.44",
        "baseStationZ": "1.3"
      }
    ]
  }
}
```

使用方式：

- `mapImgViewUrl` 是已经从 CPA 中解析并上传的地图文件地址，前端可以直接作为图片地址加载。
- `mapWidthPixel`、`mapHeightPixel` 是地图图片尺寸。
- `mapMetersPerPixel`、`mapOriginPixelX`、`mapOriginPixelY` 用于把 CLE 坐标换算成图片坐标。
- `baseStationList` 是当前地图下的基站，因此能够确定每个基站属于哪张地图，并同时取得在线状态和 `X/Y/Z` 安装位置。
- `mapModelType` 为 `image` 时可以直接按图片显示；应以接口实际返回的类型和 `mapImgViewUrl` 为准。

当前 IoT 平台只需要基站在线状态和位置，推荐流程如下：

1. 页面启动时请求一次 `/map/tree`，取得可用的 `mapId`。
2. 选择地图后请求 `/map/id`，加载 `mapImgViewUrl` 并保存该地图的 `baseStationList`。
3. 后续只刷新基站在线状态，不要反复下载地图图片。可以定时重新请求 `/map/id`，也可以轮询第 5 节的 `/iot/status`，再通过 `baseStationMac` 合并状态。

### 6.3 基站坐标显示到地图图片

如果直接使用普通图片或 Canvas，根据接口返回值计算图片坐标：

```text
图片 X = baseStationX / mapMetersPerPixel - mapOriginPixelX
图片 Y = mapOriginPixelY - baseStationY / mapMetersPerPixel
```

`baseStationZ` 表示高度，不参与二维平面图的 X/Y 定位。若继续使用原前端的 AntV L7 地图组件，直接沿用现有坐标转换和基站绘制代码即可。

### 6.4 可以沿用的地图相关代码

| 用途 | 文件与行号 | 可沿用内容 |
|---|---|---|
| 地图 REST 接口封装 | `xiyou_web_code/src/api/path/pub.js:13-26` | `/map/tree` 和 `/map/id` 的前端请求封装 |
| 地图树处理 | `xiyou_web_code/src/mixins/mixin-map.js:28-79` | 把建筑、CPA 项目、地图转换为前端树形选择数据 |
| 地图详情加载流程 | `xiyou_web_code/src/views/home/mixins/card1.js:33-68` | 按 `mapId` 获取详情、加载底图和基站 |
| 楼层地图预览 | `xiyou_web_code/src/views/system-set/floor-set/components/FloorView/index.vue:44-95` | 获取地图详情并初始化地图预览 |
| 地图图片渲染 | `xiyou_web_code/src/components/SlL7/mixins/map.js:106-144` | 使用 `mapImgViewUrl`、图片尺寸和原点参数加载底图 |
| 坐标转换 | `xiyou_web_code/src/components/SlL7/mixins/l7.js:13-49` | CLE 坐标与前端地图坐标互转 |
| 基站绘制与状态显示 | `xiyou_web_code/src/components/SlL7/mixins/jizhan.js:32-115` | 读取 `baseStationList`，转换坐标并显示在线状态 |
| CPA 上传接口封装 | `xiyou_web_code/src/api/path/floor-set.js:13-38` | 上传 CPA、查询生效结果和删除 CPA；仅配置功能需要 |
| 地图查询接口 | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/controller/MapController.java:23-55` | `/map/tree` 和 `/map/id` 后端接口 |
| 地图响应字段 | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/MapDTO.java:19-69` | 地图图片地址、尺寸、比例、原点和基站列表 |
| 地图树生成 | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/MapService.java:50-61` | 建筑、CPA 项目和地图三级结构 |
| 云端接收 CPA | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/controller/LocalServerController.java:124-159` | CPA 上传和生效结果接口 |
| 云端下发 CPA | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/LocalServerService.java:348-382` | 保存 CPA 并通过 MQTT 下发给本地服务 |
| 本地服务加载 CPA | `xiyou-server/xiyou-local/src/main/java/com/ifengniao/server/xiyoulocal/service/MQTTService.java:73-86` | 下载 CPA、启动 CLE 并重新获取设备信息 |
| 从 CLE 读取地图 | `xiyou-server/xiyou-local/src/main/java/com/ifengniao/server/xiyoulocal/config/Startup.java:351-384` | 读取 CPA 项目及其地图列表 |
| 解析地图和基站 | `xiyou-server/xiyou-local/src/main/java/com/ifengniao/server/xiyoulocal/config/Startup.java:395-500` | 读取地图参数、上传地图图片并解析基站坐标 |
| 云端保存解析结果 | `xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/LocalServerService.java:154-220` | 保存地图参数、地图图片和地图下的基站 |

对于当前 IoT 平台，优先沿用 `pub.js`、`mixin-map.js`、`map.js`、`l7.js` 和 `jizhan.js`。CPA 上传及解析代码继续保留在西柚系统中，IoT 平台无需复制。
