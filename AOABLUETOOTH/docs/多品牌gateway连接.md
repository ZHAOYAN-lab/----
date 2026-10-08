# 多品牌网关聚合接口实现与新设备接入说明

## 1. 目的

西柚、DSGW-200、Quuppa 使用不同的数据来源和协议：

- 西柚原有设备：数据库和原有 MQTT 链路。
- DSGW-200：MQTT 定位消息。
- Quuppa：HTTP API。

聚合层负责屏蔽不同品牌的协议差异，将设备状态和定位坐标转换为统一格式。上层 IoT 平台只需要调用：

```text
GET /ifengniao/cloud/server/xiyou/iot/status
GET /ifengniao/cloud/server/xiyou/iot/positions
```

这里的“一次调用”是指对上层平台只提供一个统一入口。后台仍然按照各品牌自己的协议采集数据。

## 2. 整体实现流程

```text
application.properties
  │
  ├─ 西柚数据库 ──────────────────────────────┐
  │                                           │
  ├─ DSGW-200 MQTT → 原始消息解析 → 坐标缓存 ─┼─→ 统一聚合
  │                                           │       │
  └─ Quuppa HTTP → 原始响应解析 → 实时坐标 ───┘       │
                                                      ├─→ /iot/status
                                                      └─→ /iot/positions
```

代码执行顺序：

1. 从配置文件读取各品牌的连接地址、设备信息和坐标偏移量。
2. 后台持续订阅 DSGW-200 MQTT 定位消息。
3. 调用接口时读取西柚数据库数据。
4. 调用接口时通过 Quuppa HTTP API 读取实时数据。
5. 将不同品牌的数据转换成统一 DTO。
6. 合并后通过统一 REST 接口返回。

## 3. 配置层

代码路径：

- [`application.properties`](../xiyou-server/xiyou-cloud/src/main/resources/application.properties)

主要配置：

```properties
# DSGW-200
application.systemConfig.external.dusunMqttUri=tcp://192.168.0.12:1883
application.systemConfig.dusunPositionTopic=silabs/aoa/position/multilocator-test_room/#
application.systemConfig.external.dusunMasterIp=192.168.0.12
application.systemConfig.external.dusunSlaveIp=192.168.0.7
application.systemConfig.external.dusunOffsetX=0
application.systemConfig.external.dusunOffsetY=0

# Quuppa
application.systemConfig.external.quuppaBaseUrl=http://192.168.0.72:8080/qpe
application.systemConfig.external.quuppaLocatorId=a4da22ef5da5
application.systemConfig.external.quuppaLocatorIp=192.168.0.229
application.systemConfig.external.quuppaLocatorX=3.00
application.systemConfig.external.quuppaLocatorY=2.50
application.systemConfig.external.quuppaLocatorZ=2.70
application.systemConfig.external.quuppaOffsetX=0
application.systemConfig.external.quuppaOffsetY=0

# 定位数据有效期
application.systemConfig.external.positionExpireMillis=30000
```

配置项作用：

| 配置 | 作用 |
|---|---|
| `dusunMqttUri` | DSGW-200 MQTT Broker 地址 |
| `dusunPositionTopic` | DSGW-200 定位消息订阅 Topic |
| `dusunMasterIp`、`dusunSlaveIp` | DSGW 主从设备 IP |
| `quuppaBaseUrl` | Quuppa Positioning Engine HTTP 地址 |
| `quuppaLocatorId` | 用于判断在线状态的 Locator ID |
| `quuppaLocatorIp` | 对外返回的 Locator IP |
| `quuppaLocatorX/Y/Z` | Locator 安装坐标 |
| `OffsetX/OffsetY` | 将不同品牌坐标移动到同一地图坐标系 |
| `positionExpireMillis` | DSGW 坐标超过该时间未更新就从结果中删除 |

生产环境应在对应 Profile 或外部配置文件中填写真实值，不应把设备密码和访问凭证提交到仓库。

## 4. 品牌数据接入层

### 4.1 西柚原有设备

代码路径：

- [`OverviewService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/OverviewService.java)

`deviceStatus()` 从数据库一次查询：

- `gateways`：西柚网关。
- `baseStations`：西柚基站。
- `beacons`：西柚信标。

这里负责读取原有系统已经保存的数据，不直接连接外部品牌设备。

### 4.2 DSGW-200

代码路径：

- [`ExternalPositionService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/ExternalPositionService.java)
- [`MQTTService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/MQTTService.java)
- [`PositionDusun.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/PositionDusun.java)

处理流程：

```text
连接 DSGW MQTT
  → 订阅配置的 Topic
  → 收到 JSON 消息
  → 解析为 PositionDusun
  → 从 Topic 最后一段取得 tagId
  → 将秒级时间戳转换为毫秒
  → 转换成 PositionCommon
  → 增加 X/Y 偏移量
  → 按 tagId 缓存最新坐标
```

`ExternalPositionService.connectDusun()` 负责 MQTT 连接、订阅和重连。

`MQTTService.handleDusunPositionMessage()` 负责：

- 判断 Topic 是否匹配。
- 解析原始 JSON。
- 解析标签 ID。
- 统一时间戳单位。
- 转换坐标对象。

### 4.3 Quuppa

代码路径：

- [`ExternalPositionService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/ExternalPositionService.java)
- [`PositionQuuppa.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/PositionQuuppa.java)

定位坐标处理流程：

```text
调用 /getTagData?format=defaultLocation
  → 解析 tags 数组
  → 过滤 locationType 不是 position 的数据
  → 从 location 数组拆出 X/Y/Z
  → 增加 Quuppa X/Y 偏移量
  → 转换成 PositionCommon
```

基站在线状态处理流程：

```text
调用 /getLocatorInfo?humanReadable=true
  → 查找配置的 Locator ID
  → connection=ok 时判断为在线
```

Quuppa 坐标是在调用 `/iot/positions` 时实时读取的；DSGW 坐标来自后台 MQTT 缓存。

## 5. 统一数据模型

代码路径：

- [`PositionCommon.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/PositionCommon.java)
- [`PositionDusun.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/PositionDusun.java)
- [`PositionQuuppa.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/PositionQuuppa.java)
- [`BaseStationDTO.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/BaseStationDTO.java)

不同品牌定位数据最终统一为：

```json
{
  "tagId": "标签唯一标识",
  "x": 1.2,
  "y": 3.4,
  "z": 1.5,
  "timestamp": 1780000000000,
  "source": "quuppa"
}
```

统一规则：

- `x/y/z`：单位为米。
- `timestamp`：Unix 毫秒时间戳。
- `source`：品牌来源，例如 `quuppa`、`dusun`。
- 标签去重键：`source:tagId`。
- 外部基站统一转换成 `BaseStationDTO` 后并入原有基站数组。

## 6. 聚合处理层

代码路径：

- [`ExternalPositionService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/ExternalPositionService.java)
- [`OverviewService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/OverviewService.java)

### 6.1 坐标聚合

`ExternalPositionService.getPositions()`：

1. 删除超过 `positionExpireMillis` 的 DSGW 坐标。
2. 读取 Quuppa 实时坐标。
3. 加入 DSGW MQTT 缓存坐标。
4. 使用 `source:tagId` 防止不同品牌 ID 冲突。
5. 返回统一的 `PositionCommon` 数组。

### 6.2 基站状态聚合

`ExternalPositionService.getBaseStations()`：

1. 检测 DSGW Master 的 1883 端口。
2. 检测 DSGW Slave 的 22 端口。
3. 调用 Quuppa Locator 接口检查在线状态。
4. 将外部设备转换为 `BaseStationDTO`。
5. 由控制器加入西柚原有基站列表。

当前外部基站列表固定包含：

- DSGW-200 Master。
- DSGW-200 Slave。
- Quuppa L001。

因此当前实现不是自动发现任意数量的外部网关。增加新网关时，需要按第 9 节处理。

## 7. REST 接口输出层

代码路径：

- [`IotController.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/controller/IotController.java)

### 7.1 设备状态

```http
GET /ifengniao/cloud/server/xiyou/iot/status
Authorization: Bearer <JWT>
```

执行流程：

```text
IotController.status()
  → OverviewService.deviceStatus()
  → 读取西柚网关、基站、信标
  → ExternalPositionService.getBaseStations()
  → 加入 DSGW 和 Quuppa 基站
  → 返回 gateways、baseStations、beacons
```

### 7.2 统一定位坐标

```http
GET /ifengniao/cloud/server/xiyou/iot/positions
Authorization: Bearer <JWT>
```

执行流程：

```text
IotController.positions()
  → ExternalPositionService.getPositions()
  → 读取 Quuppa HTTP 实时坐标
  → 读取 DSGW MQTT 缓存坐标
  → 返回 PositionCommon 数组
```

## 8. 当前不属于正式主链路的文件

代码路径：

- [`QuuppaService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/QuuppaService.java)
- [`MQTTService.java.bak`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/MQTTService.java.bak)

说明：

- `QuuppaService.java` 是 UDP 接收试验代码，只解析并打印日志，没有把数据交给 `/iot/positions`。当前文件还缺少必要的 Java import，不应当作正式聚合链路使用。
- `MQTTService.java.bak` 是备份文件，不参与 Maven 编译，也不参与系统运行。

## 9. 接入新网关设备

首先判断新设备属于哪一种情况。

### 9.1 已支持品牌的新标签

如果只是新增 Quuppa 或 DSGW 标签，并且数据仍通过现有 HTTP/MQTT协议提供：

1. 确认标签数据能从原品牌接口或 Topic 中读取。
2. 确认 `tagId` 可以稳定取得。
3. 确认坐标单位为米。
4. 确认时间戳最终转换为毫秒。
5. 根据现场地图调整 `OffsetX/OffsetY`。
6. 调用 `/iot/positions` 检查新标签。

通常不需要修改 `IotController`。

### 9.2 已支持品牌的新网关或 Locator

当前 DSGW 和 Quuppa 基站信息是在 `ExternalPositionService.getBaseStations()` 中固定构建的，不能自动增加。

接入步骤：

1. 收集新设备信息：
   - 品牌和型号。
   - 稳定的设备 ID 或 MAC。
   - IP和端口。
   - MQTT Topic 或 HTTP API。
   - 安装坐标 X/Y/Z。
   - 所属地图。
2. 将连接信息放入配置文件，不要直接散落在业务代码中。
3. 在 `ExternalPositionService` 中增加新设备的在线检查。
4. 将设备状态转换成 `BaseStationDTO`。
5. 为设备分配不会与数据库ID冲突的稳定ID。当前外部设备使用负数ID。
6. 如果设备产生定位坐标，将数据转换成 `PositionCommon`。
7. 调整坐标偏移量，使新设备与现有地图对齐。
8. 验证 `/iot/status` 和 `/iot/positions`。

如果只是临时增加一台同品牌设备，可以在现有服务中增加一条配置和一条设备构建逻辑。只有设备数量需要频繁变化时，才需要改成配置列表或数据库管理。

### 9.3 接入全新品牌

全新品牌需要增加一个品牌适配层，但不需要修改上层 IoT 平台的接口调用方式。

推荐步骤：

#### 第一步：确认品牌协议

确认厂商提供：

- HTTP、MQTT、TCP、UDP或SDK中的哪一种接入方式。
- 连接地址和鉴权方式。
- 在线状态接口。
- 定位数据格式。
- 设备唯一标识。
- 坐标单位、坐标原点和方向。
- 时间戳单位和时区。
- 断线与重连要求。

#### 第二步：增加原始数据 DTO

在目录中增加该品牌的原始数据模型：

```text
xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/dto/
```

例如：

```text
PositionNewBrand.java
```

该对象只负责接收厂商原始字段，不直接返回给IoT平台。

#### 第三步：增加品牌接入服务

在目录中增加：

```text
xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/
```

例如：

```text
NewBrandPositionService.java
```

该服务负责：

- 建立HTTP、MQTT或其他协议连接。
- 解析厂商数据。
- 处理超时和重连。
- 读取设备在线状态。
- 将定位结果转换成 `PositionCommon`。
- 将基站状态转换成 `BaseStationDTO`。

一个品牌不可用时，应返回该品牌的空数据或离线状态，不能让整个聚合接口失败。

#### 第四步：加入聚合结果

在：

- [`ExternalPositionService.java`](../xiyou-server/xiyou-cloud/src/main/java/com/ifengniao/server/xiyoucloud/service/ExternalPositionService.java)

将新品牌加入：

- `getPositions()`：合并新品牌定位数据。
- `getBaseStations()`：合并新品牌网关或基站状态。

只要仍然返回 `PositionCommon` 和 `BaseStationDTO`，通常不需要修改 `IotController`。

#### 第五步：增加配置

在部署环境配置文件中增加：

```properties
application.systemConfig.external.newBrand.baseUrl=
application.systemConfig.external.newBrand.username=
application.systemConfig.external.newBrand.password=
application.systemConfig.external.newBrand.offsetX=0
application.systemConfig.external.newBrand.offsetY=0
application.systemConfig.external.newBrand.positionExpireMillis=30000
```

真实配置名称以厂商协议为准。密码、Token、证书等敏感信息应通过外部配置或环境变量提供。

#### 第六步：保持统一输出规则

新品牌必须遵守：

```text
定位结果 → PositionCommon
基站状态 → BaseStationDTO
source   → 新品牌的小写标识
坐标单位 → 米
时间戳   → Unix毫秒
```

例如：

```json
{
  "tagId": "tag-001",
  "x": 2.3,
  "y": 6.8,
  "z": 1.2,
  "timestamp": 1780000000000,
  "source": "newbrand"
}
```

## 10. 新设备接入验收

### 网络与配置

- 后端可以访问新设备IP和端口。
- MQTT Topic、HTTP地址或SDK参数正确。
- 敏感信息没有写入日志。
- 服务重启后能自动恢复连接。

### 设备状态

- `/iot/status` 返回新设备。
- 设备ID稳定且不与已有设备冲突。
- 在线和离线状态能够正确变化。
- 安装坐标和设备名称正确。

### 定位坐标

- `/iot/positions` 返回新品牌数据。
- `source` 正确。
- `tagId` 稳定。
- 坐标单位为米。
- 时间戳为毫秒。
- 旧坐标会在过期后删除。
- 坐标经过偏移后能正确显示在同一张地图。

### 故障隔离

- 关闭新品牌设备后，其他品牌数据仍能正常返回。
- 请求超时不会无限等待。
- 错误数据不会造成整个接口报错。
- 设备恢复后能够自动重新接入。

## 11. 最终职责总结

| 类别 | 代码 | 职责 |
|---|---|---|
| 配置 | `application.properties` | 保存各品牌连接参数和坐标参数 |
| 西柚数据 | `OverviewService` | 查询西柚原有网关、基站和信标 |
| DSGW接入 | `ExternalPositionService`、`MQTTService` | 订阅、解析和缓存DSGW坐标 |
| Quuppa接入 | `ExternalPositionService` | 通过HTTP读取坐标和Locator状态 |
| 原始模型 | `PositionDusun`、`PositionQuuppa` | 接收不同品牌原始数据 |
| 统一模型 | `PositionCommon`、`BaseStationDTO` | 对上层提供统一字段 |
| 聚合 | `ExternalPositionService` | 合并多品牌设备状态和坐标 |
| REST入口 | `IotController` | 提供 `/iot/status` 和 `/iot/positions` |

