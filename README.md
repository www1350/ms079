ms079
=====

冒险岛 v079 版本，是大巨变前最经典的版本。

---

## 一、运行条件

**运行必备条件——数据库：**

- [MySQL 5.7.30+][3] （提取码：6ifn）
  - 注意，原版本在 MySQL 4.x 环境下运行，我自己的服务器，使用阿里云的 5.7.32-log 版本，可以正常运行
- PostgreSQL（暂未支持）
- Sqlite3（暂未支持）

### 1.1 开发环境

- [JDK 1.8][1]：注意，原版本仅在 Java7 JRE 环境运行，迁移到 Java8 将存在 js 脚本导入的问题，目前已修复此兼容性问题
- [IntelliJ IDEA][2]：推荐使用的开发工具

启动入口是 `com.github.mrzhqiang.maplestory.MapleStoryApplication.java` 的 `main` 方法。

### 1.2 运行环境

1. 安装 [JDK 1.8][1] （或以上）运行环境
2. 设置 `JAVA_HOME` 环境变量
3. 使用启动脚本启动即可
4. 【推荐】使用 GUI 启动，获得更多运维功能

## 二、如何编译？

编译通常使用 [Maven][4] 插件，如果你是开发者，建议通过 IDEA 工具进行编译。

**更新：目前已加入 assembly 编译插件，可以同时打包 `wz` 和 `脚本` 以及其他资源到 `ms079-[version]-dist.zip` 压缩文件中。此压缩文件可以作为绿色安装包使用，只需要解压出来，安装运行环境，以及初始化数据库，然后就可以运行了。**

### 2.1 IDEA 工具

展开右侧栏 Maven 菜单中的 Lifecycle 选项，双击 `compile` 命令，即可生成编译文件。

**需要打包为 `ms079-[version].jar` 的话，则使用 `package` 命令即可。**

### 2.2 Maven 插件

在项目根目录下打开终端（需要安装 Maven）：

- 编译：`mvn clean compile -DskipTests`
- 打包：`mvn clean package -DskipTests`
- 跳过测试可大幅加快构建速度

> JDK 版本：项目基于 JDK 8，实测 JDK 17 也可正常运行。

## 三、初始化数据库

推荐使用免费工具连接 MySQL：

- [HeidiSQL][5] — 免费、免安装、超轻量（推荐）
- [DBeaver][8] — 免费、功能全面

数据库连接信息参见 `服务端配置.ini`，默认如下：

| 配置 | 值 |
|------|-----|
| Host | `localhost` |
| Port | `3306` |
| 用户名 | `root` |
| 密码 | `123456` |
| 数据库 | `maplestory` |

连接后执行以下步骤：

1. 创建名为 `maplestory` 的数据库，字符集选 `utf8`
2. 在数据库上右键 → Execute SQL File...
3. 选择项目中的 `db/ms079.sql` 文件执行

## 四、如何运行？

运行分两步：首先启动服务端，然后安装客户端进行登录。

### 4.1 启动服务端

**前提是 `JAVA_HOME` 环境变量正常，以及数据库初始化完毕。**

1. 打包（已有 `ms079-[version]-dist.zip` 文件，可忽略此步骤）
   - 展开 IDEA 右侧栏 Maven 菜单中的 `Lifecycle` 选项
   - 双击 `clean` 清理旧文件
   - 双击 `package` 进行打包
   - 确定已生成 `/target/ms079-[version]-dist.zip` 文件
   - 【可选】或者通过 Maven 插件，在根目录下使用 `mvn clean package` 命令进行打包
2. 解压 zip 文件到某一个文件夹
3. 进入文件夹，修改 `服务端配置.ini` 配置文件中的参数（主要是数据库的账号密码）
4. 打开 `启动服务端-GUI.bat`，点击【启动服务端】按钮
5. 【可选】或者也可以打开 `启动服务端-命令行.bat` or `启动服务端-命令行.sh`
6. 等待显示【启动成功，可以进入游戏】类似的信息即可

**注意：新版本已无法通过 JDK7 来编译，要使用 JDK7 版本编译，请自行找到第一次提交的 git 版本。**

*提示：`ms079-[version]-dist.tar.gz` 文件是 Linux 服务器的压缩文件，目前没有经过 Linux 服务器的测试。*

### 4.2 客户端登录

**Windows 10/11 推荐方案（绕过 HShield）：**

旧版 HShield 替换方案在 Windows 11 Canary 及部分 Win10 版本上无法工作（内核驱动拦截导致白屏）。推荐使用 CMS v79-v104 登入器，直接绕过 HShield：

1. 安装 [冒险岛v079客户端][6]
2. 下载 [CMSLauncher][7] 最新 Release（`Launcher.exe` + `Hook.dll`）
3. 将 `Launcher.exe` 和 `Hook.dll` 放到客户端根目录
4. 运行 `Launcher.exe` 即可连接

**端口说明：** 登入器 Hook 将 CMS 官方域名重定向到 `127.0.0.1`，默认连接 **8484** 端口（CMS 标准登录端口）。服务端配置中 `server.login.port` 需设为 `8484`。

**V079登录器.bat：**
```bat
taskkill /im MapleStory.exe /f
Launcher.exe
```

如需联网：编辑 `服务端配置.ini` 中的 IP 地址，并在云服务器安全组开放 8484 及频道端口（7575+）。

---

**旧方案（仅限 Win7/Win8，不推荐）：**

<details>
<summary>HS 文件替换方案（点击展开）</summary>

1. 删除客户端中的 HShield 目录
2. 下载 079 私服过 HS 文件进行替换
3. 编辑 `HShield/ehsvc.ini`，设置 `GamePath=` 为 MapleStory.exe 完整路径
4. 运行 `MapleStory.exe 127.0.0.1 9595`

此方案在 Windows 10 及以上版本可能因 HShield 驱动加载失败导致白屏。
</details>

---

# 声明

仅供个人学习交流使用，不得用于任何商业途径，请在下载 24 小时后删除。



[1]:https://alywp.net/5whNJG
[2]:https://www.jetbrains.com/idea/
[3]:https://pan.baidu.com/s/1v-2jXg9xqNmo5ww5YjUhQQ
[4]:https://maven.apache.org/download.cgi
[5]:https://www.heidisql.com/download.php
[6]:https://alywp.net/2bBtbJ
[7]:https://github.com/zhyonc/CMSLauncher/releases
[8]:https://dbeaver.io/download/