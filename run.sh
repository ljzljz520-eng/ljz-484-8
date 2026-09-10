#!/usr/bin/env bash
# 家谱故事整理系统 —— 一键编译并启动（Linux / macOS）
set -e
cd "$(dirname "$0")"
mkdir -p out
echo ">>> 编译中..."
javac -encoding UTF-8 -d out src/*.java
echo ">>> 启动中..."
java -cp out Main
