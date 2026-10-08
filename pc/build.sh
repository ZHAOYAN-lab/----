#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/../AOABLUETOOTH/xiyou_web_code"
VUE_CLI_SERVICE_CONFIG_PATH="$DIR/vue.config.js" node node_modules/@vue/cli-service/bin/vue-cli-service.js build
