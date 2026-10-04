# ビーコンファインダー (Beacon Finder) Android App

付属の `BaseSDK.jar` をベースに開発された、LAN内基地局および発光ビーコンを管理・制御するAndroidクライントアプリです。

## 主な機能

- **QRコードによる基地局の追加・接続**: SN、MACアドレス、IPアドレス、JSON形式など多様なQRコードに対応
- **LAN内自動検出 & 手動入力**: QRコードが読み取れない場合でも、同一Wi-Fi上の基地局をワンタップで自動検索またはIP/SN手動入力で接続
- **ビーコンの登録・管理**: 現在の基地局配下にビーコンをQRコードスキャンで追加し、個別保存
- **一括・個別制御**: ビーコンの単体点灯、一括全選択点灯、消灯停止
- **多彩な発光設定**: 7色のLED（赤・黄・青・緑・シアン・白・紫）、点滅/常時点灯、ビープ音のON/OFF切り替え
- **リアルタイム状態監視**: 基地局オンライン状態、コマンド実行結果、F5ビーコン電圧・動作状態のリアルタイム表示

## 対応QRコード形式

### 基地局 (Base Station)
- プレーンSN: `QJ000000000001`, `WL0000000001`, `YZ202401010001` 等
- プレフィックス付き: `BASE:QJ000000000001`, `SN: QJ000000000001`, `ID: QJ000000000001`, `MAC: 8C:19:2D:C4:E2:82` 等
- JSON形式: `{"sn":"QJ000000000001"}`, `{"id":"WL0000000001"}`, `{"ip":"192.168.1.50"}`, `{"mac":"8C:19:2D:C4:E2:82"}` 等
- IPアドレス直接形式: `192.168.1.50` または `192.168.1.50:5000`
- URL形式: `http://192.168.1.50:5000/` 等

### ビーコン (Beacon)
- 10進数コード、`TAG:` / `BEACON:` / `ID:` / `CODE:` プレフィックス付き、または `{"code":"1234567890"}`
- 10桁を超える場合は末尾10桁を自動抽出し、4バイト暗号コードへ変換

## ビルド方法

1. Android Studio（JDK 17または21）で本ディレクトリを開きます。
2. Android 7.0 (API 24) 以上の端末を接続します（端末と基地局は同一LAN/Wi-Fiに接続してください）。

### デバッグビルド
```bash
./gradlew assembleDebug
```
APK出力先: `app/build/outputs/apk/debug/app-debug.apk`

### リリースビルド
```bash
./gradlew assembleRelease
```
APK出力先: `app/build/outputs/apk/release/app-release.apk`
署名設定は `keystore.properties` に記述され、キーストアは `app/release/beacon-finder-release.jks` を使用します。

## SDK パラメータ仕様

- 点灯時間: 60秒（SDK `workTime=20`、単位3秒）
- 点滅間隔: 500ms（`intervalTime=5`、単位100ms）
- 消灯コマンド: `rgb=0x08`
- これらのパラメータは `BaseStationGateway.java` に集約されています。
