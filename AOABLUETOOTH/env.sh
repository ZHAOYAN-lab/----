#!/usr/bin/env bash

export XIYOU_ENV_HOME="${XIYOU_ENV_HOME:-$HOME/.local/share/xiyou-env}"
export JAVA_HOME="$XIYOU_ENV_HOME/jdk17/Contents/Home"
export PATH="$XIYOU_ENV_HOME/node/bin:$JAVA_HOME/bin:$PATH"
