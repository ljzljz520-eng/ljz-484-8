@echo off
rem 家谱故事整理系统 —— 一键编译并启动（Windows）
cd /d %~dp0
if not exist out mkdir out
echo ^>^>^> 编译中...
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
  echo 编译失败，请确认已安装 JDK 8 或以上版本
  pause
  exit /b 1
)
echo ^>^>^> 启动中...
java -cp out Main
