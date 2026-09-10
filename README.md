# 家谱故事整理系统

一个记录家族人物、年代与故事章节的小型系统：浏览器前端负责浏览与编辑，Java 后端把数据保存为**本地 JSON 文件**。私密故事只在编辑端可见。

## 功能一览

- **人物管理**：姓名、性别、世代（第几世）、生卒年、父母、简介
- **故事章节**：标题、起止年份、相关人物（可多人）、正文、公开状态
- **浏览端**：
  - 按人物：左侧按世代分组的人物列表，点选后查看生平与相关故事
  - 按年代：故事按十年一档（如「1970 年代」）分组的时间线
  - 浏览端**只显示公开故事**
- **编辑端**：维护人物与故事；私密故事带 🔒 标记，仅在此可见
- **本地文件存储**：所有数据保存在 `data/` 目录的 JSON 文件中，可直接查看、备份、用 Git 管理

## 运行环境

- JDK 8 或以上（使用 JDK 自带的 `com.sun.net.httpserver`，**无需 Maven/Gradle，无第三方依赖**）

## 启动命令

### Linux / macOS

```bash
./run.sh
```

### Windows

```bat
run.bat
```

### 手动编译启动

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/*.java
java -cp out Main
```

启动后打开浏览器访问 **http://localhost:8080** ，按 `Ctrl+C` 停止。

### 可配置项（环境变量）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `PORT` | `8080` | 服务端口 |
| `DATA_DIR` | `data` | 数据文件目录 |
| `WEB_DIR` | `web` | 前端静态文件目录 |
| `EDIT_PASSWORD` | （空） | 设置后，编辑操作与查看私密故事需输入口令 |

示例：

```bash
PORT=9000 EDIT_PASSWORD=family123 ./run.sh
```

## 文件结构

```
.
├── src/                    # Java 后端
│   ├── Main.java           # 入口：启动 HTTP 服务
│   ├── ApiHandler.java     # /api 路由、公开/私密过滤、口令校验
│   ├── StaticHandler.java  # 静态文件服务（web/ 目录）
│   ├── Store.java          # 存储层：读写 data/*.json（原子写入）
│   ├── Person.java         # 人物模型
│   ├── Story.java          # 故事模型
│   └── Json.java           # 极简 JSON 解析/序列化（无第三方依赖）
├── web/                    # 前端（原生 HTML/CSS/JS）
│   ├── index.html          # 单页：浏览 / 编辑两种模式
│   ├── style.css
│   └── app.js
├── data/                   # 数据文件（本地 JSON，可直接编辑）
│   ├── people.json         # 人物
│   └── stories.json        # 故事（"public": false 即私密）
├── run.sh                  # 一键编译启动（Linux/macOS）
├── run.bat                 # 一键编译启动（Windows）
└── README.md
```

## 数据文件格式

### `data/people.json`（人物数组）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 字符串 | 唯一标识（新增时由后端生成） |
| `name` | 字符串 | 姓名（必填） |
| `gender` | 字符串 | 男 / 女 / 空 |
| `generation` | 数字 | 世代，第几世 |
| `birthYear` / `deathYear` | 数字或 null | 生卒年，健在时 `deathYear` 为 null |
| `fatherId` / `motherId` | 字符串或 null | 父母的人物 id |
| `bio` | 字符串 | 简介 |

### `data/stories.json`（故事数组）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | 字符串 | 唯一标识 |
| `title` | 字符串 | 标题（必填） |
| `personIds` | 字符串数组 | 相关人物 id 列表 |
| `yearStart` / `yearEnd` | 数字或 null | 起止年份；年代按 `yearStart` 所在十年分组 |
| `content` | 字符串 | 正文 |
| `public` | 布尔 | **false = 私密，仅编辑端可见** |
| `createdAt` / `updatedAt` | 数字 | 时间戳（毫秒） |

## 示例故事

系统自带「张家」三代的示例数据（就在 `data/` 目录里，可直接改）：

| 故事 | 年代 | 相关人物 | 公开状态 |
|---|---|---|---|
| 渡口边的少年 | 1943–1949 | 张守义 | 公开 |
| 一车嫁妆 | 1951 | 张守义、李秀兰 | 公开 |
| 恢复高考那一年 | 1977–1978 | 张建国 | 公开 |
| 分家风波（内部记录） | 1983–1984 | 张守义、张建国 | 🔒 私密 |
| 南下的绿皮火车 | 1992–1995 | 张建国、王桂芳 | 公开 |
| 老屋檐下的全家福 | 1998 | 全家五口 | 公开 |

- 在「浏览」页只能看到 5 篇公开故事；
- 切换到「编辑」页的「故事管理」，才能看到带 🔒 的《分家风波（内部记录）》。

想从零开始记录自己的家谱：删除 `data/` 下两个 JSON 文件（或清空为 `[]`）即可，系统会在首次保存时重新创建。

## 私密机制说明

1. 故事的 `public` 字段为 `false` 时即为私密；
2. 浏览端所有请求都带 `publicOnly=true`，**后端**只返回公开故事（不是前端隐藏，私密数据根本不会发到浏览器）；
3. 编辑端请求全量数据；若启动时设置了 `EDIT_PASSWORD`，查看私密故事及一切写操作都需要在页面提示时输入口令（请求头 `X-Edit-Password`）；
4. 注意：本系统面向家庭单机/内网使用，口令只是基础防护，请勿直接暴露到公网。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/people` | 人物列表 |
| POST | `/api/people` | 新增人物 ✎ |
| PUT | `/api/people/{id}` | 更新人物 ✎ |
| DELETE | `/api/people/{id}` | 删除人物（故事保留，解除关联）✎ |
| GET | `/api/stories?publicOnly=true&personId=&decade=` | 故事列表，可按人物/年代过滤 |
| POST | `/api/stories` | 新增故事 ✎ |
| PUT | `/api/stories/{id}` | 更新故事 ✎ |
| DELETE | `/api/stories/{id}` | 删除故事 ✎ |
| GET | `/api/config` | 查询是否需要编辑口令 |

✎ = 编辑操作：设置了 `EDIT_PASSWORD` 时需携带请求头 `X-Edit-Password`；`publicOnly=false` 同样需要。

## 备份

直接复制整个 `data/` 目录即可；也可以把 `data/` 纳入 Git 做版本管理，追溯每一次修改。
