# java-kgcheckin

## 免责声明

> [!important]
>
> 1. 本项目仅供学习使用，请尊重版权，请勿利用此项目从事商业行为及非法用途!
> 2. 使用本项目的过程中可能会产生版权数据。对于这些版权数据，本项目不拥有它们的所有权。为了避免侵权，使用者务必在 24小时内清除使用本项目的过程中所产生的版权数据。
> 3. 由于使用本项目产生的包括由于本协议或由于使用或无法使用本项目而引起的任何性质的任何直接、间接、特殊、偶然或结果性损害（包括但不限于因商誉损失、停工、计算机故障或故障引起的损害赔偿，或任何及所有其他商业损害或损失）由使用者负责。
> 4. **禁止在违反当地法律法规的情况下使用本项目。** 对于使用者在明知或不知当地法律法规不允许的情况下使用本项目所造成的任何违法违规行为由使用者承担，本项目不承担由此造成的任何直接、间接、特殊、偶然或结果性责任。
> 5. 音乐平台不易，请尊重版权，支持正版。
> 6. 本项目仅用于对技术可行性的探索及研究，不接受任何商业（包括但不限于广告等）合作及捐赠。
> 7. 如果官方音乐平台觉得本项目不妥，可联系本项目更改或移除。

基于 [develop202/kgcheckin](https://github.com/develop202/kgcheckin) 和 [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) 开发的 Java 版本，用于酷狗音乐自动签到领取 VIP，方便本地或 NAS 等方式运行。需使用Java 21或更高版本。

> [!warning]
> 注意事项
>
> 若登录后听歌领取失败，请到APP 活动中心->天天签到领VIP(这个活动新用户好像没有) 查看当日是否已经领取VIP。

## 与上游仓库的区别

|        | 上游 (Node.js)               | 本项目 (Java)               |
|--------|----------------------------|--------------------------|
| 用户信息存储 | GitHub Access Token + 环境变量 | 本地 `users/` 目录存储 JSON 文件 |
| 运行方式   | GitHub Actions 远程执行        | 本地 / NAS 直接运行            |

本项目的设计目标是方便在本地 NAS 上部署运行，无需依赖 GitHub 和环境变量，用户登录凭证直接保存在本地文件中。

## Windows 用户注意

日志框架默认使用 UTF-8 编码输出，而 Windows 终端默认使用 GBK 编码，直接运行中文会显示乱码。请在运行前先切换代码页：

```powershell
chcp 65001
```

## 构建

Maven版本：3.6+

```bash
# 编译
mvn compile

# 打包为可执行 jar
mvn package
```

打包后的 jar 文件位于 `target/kgcheckin-1.2-SNAPSHOT.jar`。

## 使用

```bash
java -jar target/kgcheckin-1.2-SNAPSHOT.jar <命令> [参数]
```

### 命令

| 命令                         | 说明                                 |
|----------------------------|------------------------------------|
| `phoneLogin --phone <手机号>` | 手机验证码登录，发送验证码后提示输入验证码              |
| `qrcodeLogin [--number N]` | 二维码登录，控制台输出二维码图片，N 为账号数量（默认 1）     |
| `checkin`                  | 遍历 `users/` 目录下所有已登录用户，自动签到领取 VIP。 |

### 示例

```bash
# 手机号登录
java -jar target/kgcheckin-1.2-SNAPSHOT.jar phoneLogin --phone 12345678910

# 二维码登录（默认 1 个账号）
java -jar target/kgcheckin-1.2-SNAPSHOT.jar qrcodeLogin

# 二维码登录（批量 3 个账号）
java -jar target/kgcheckin-1.2-SNAPSHOT.jar qrcodeLogin --number 3

# 对所有已登录用户签到
java -jar target/kgcheckin-1.2-SNAPSHOT.jar checkin
```

登录成功后，用户凭证自动保存到 `users/<userid>.json`，后续 `checkin` 命令会自动加载。

### 配置文件

`checkin` 的参数可以写在与 jar **同一目录**下的 `config.ini` 中，这样定时任务不必每次都拼命令行。格式为 INI，键值对用 `=` 分隔，以 `#` 或 `;` 开头的行是注释：

```ini
# 签到时间提前量（毫秒），对应 --rsa-delay，默认 0
rsaDelay = 1200

# 请求失败后的重试次数，默认 2。填 0 表示不重试
retryCount = 2

# 每次重试之间的固定间隔（毫秒），默认 3000
retryInterval = 3000
```

为了兼容性，仍保留命令行传入参数`--rsa-delay`且优先级比配置文件更高，建议用户尽快改为由配置文件控制。此命令行参数未来可能会被删除。

若配置文件不存在，程序会在 jar 所在目录自动生成一份带注释和默认值的 `config.ini` 并直接使用，改完下次运行生效即可，不需要手动创建。只有当该目录不可写（例如只读挂载）时，才会退回内置默认值并在日志中提示。已有的配置文件如果缺少新增的配置项，也会被自动追加到文件末尾。

### 失败重试

`checkin` 的每个请求（获取账号信息、听歌领取、广告领取、查询VIP）在失败后都会按 `retryCount` 与 `retryInterval` 重试。以下情况都算失败：

- 服务器返回了未成功的响应，例如 rsa 延迟导致时间戳过期而返回的 `token过期或账号不存在`；
- 网络异常，例如连接超时、断网。

已经成功的步骤不会被重复执行。`今日已领取`、`今天次数已用光` 属于正常的终态，不会触发重试。重试全部用尽后仍失败，才会按原有逻辑记录到异常信息中。

### 邮件通知（可选）

在 NAS 上跑定时任务时，失败往往没人注意到。开启邮件通知后，`checkin` 失败会发一封邮件提醒需要人工接管。**该功能默认关闭**，把 `mailEnabled` 改为 `true` 并填写下面的 SMTP 信息即可：

```ini
# 签到失败时是否发送邮件通知，默认 false（关闭）
mailEnabled = true

# SMTP 服务器地址，例如 smtp.qq.com
mailHost = smtp.qq.com

# SMTP 服务器端口，例如 465
mailPort = 465

# 是否使用 SSL 直连。端口 465 填 true；
# 填 false 时使用 STARTTLS，服务端不支持则退回明文
mailSsl = true

# SMTP 登录账号
mailUsername = your@qq.com

# SMTP 登录密码或授权码，多数邮箱需要填授权码而不是网页登录密码
mailPassword = xxxxxxxxxxxxxxxx

# 收件人地址，多个收件人用英文逗号分隔
mailTo = you@example.com, other@example.com
```

以下情况都会发信：有账号签到失败、`users` 目录为空、以及重试耗尽后仍未恢复的网络异常。签到成功不会发信。

若启用了但 `mailHost` / `mailUsername` / `mailTo` 没填全，程序会打一条错误日志并跳过发送，不会影响签到本身的退出码；邮件发送失败同理，只记日志。

> [!warning]
> `mailPassword` 以明文保存在 `config.ini` 中，请注意该文件的权限，并使用邮箱的授权码而不是账号登录密码。

## FAQ

### 为什么在 NAS 或低配置 Linux 上运行会提示 token过期或账号不存在？

#### 现象：
在高性能电脑（如 Windows/Mac）上运行一切正常，但将编译好的 jar 包或生成的 users/*.json 文件放到某些老旧 CPU（如 Intel J1800、N100 甚至某些 ARM 软路由）的 NAS 或 Linux 服务器上运行时，程序没有抛出 Java 异常，但会一直提示：
token过期或账号不存在, userid: xxxx

#### 原因：
这不是 Token 真的过期了，而是底层算力不足导致的时间戳防重放拦截。
酷狗 API 的加密验证极度依赖当前时间戳（clienttime），且防重放误差窗口极其严苛（通常在 1 ~ 2 秒内）。
低性能机器在执行耗时的 RSA 非对称加密及建立网络 TLS 握手连接时，可能会耗费长达 1 ~ 3 秒的时间。当请求历经波折到达酷狗服务器时，请求体中包含的 clienttime 已经成为“过期”的旧时间，从而被官方 WAF 防火墙无情拦截并返回 20006 错误码。

#### 解决方案：设置 rsaDelay（毫秒）

你可以通过该参数人为增加一个“时间提前量”（毫秒）。
例如，如果你的机器从“开始运行”到“发出网络请求”需要卡顿 1.2 秒，你可以设置为 1200。程序会在生成时间戳时自动加上 1.2 秒的余量，等 CPU 吭哧吭哧算完加密并发出去时，时间戳到达官方服务器就会刚刚好，从而完美绕过风控拦截。

设置方式二选一：写入 `config.ini` 的 `rsaDelay`（推荐），或使用命令行参数 `--rsa-delay 1200`（不推荐）。