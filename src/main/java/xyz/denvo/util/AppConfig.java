package xyz.denvo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取与 jar 位于同一目录下的 config.ini，用于取代把参数写在命令行里的做法。
 * <p>
 * 文件格式为 INI：每行 {@code 键 = 值}，以 {@code #} 或 {@code ;} 开头的行视为注释。
 * 例如 {@code rsaDelay = 1200}。目前仅 checkin 命令会用到本类，后续新增功能只需在
 * {@link #KNOWN_ENTRIES} 中追加一项即可。
 * <p>
 * 使用前需先调用一次 {@link #load()}。若配置文件不存在，会自动生成一份带默认值（含注释说明）的
 * config.ini 并直接使用；若配置文件已存在但缺少其中的某些项（例如版本升级后新增的参数），
 * 会把这些项追加到文件末尾，因此参数值始终来自文件本身，而不是分散在各调用处的兜底默认值。
 * 只有在文件无法创建、追加或读取时，才会退回调用方传入的默认值。
 */
public final class AppConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);
    public static final String FILE_NAME = "config.ini";

    /** 已知配置项：键、默认值、注释（注释不含 # 前缀，渲染时逐行补上）。 */
    private record Entry(String key, String value, String comment) {}

    /** 配置文件开头的固定说明。 */
    private static final String FILE_HEADER = """
            # java-kgcheckin 配置文件（首次运行时自动生成）
            #
            # 本文件与 jar 位于同一目录，checkin 命令从这里读取参数。
            # 以 # 或 ; 开头的行是注释。""";

    private static final List<Entry> KNOWN_ENTRIES = List.of(
            new Entry("rsaDelay", "0",
                    """
                            签到时间提前量（毫秒），对应命令行参数 --rsa-delay，默认 0。
                            低性能设备（NAS、软路由等）算 RSA 较慢，导致请求到达服务器时时间戳已过期、
                            提示「token过期或账号不存在」时，可尝试调大，例如 1200。
                            兼容性：命令行传入的参数优先级高于本文件。命令行传入方式在后续版本可能会被废弃，请尽早改为配置文件方式。"""),
            new Entry("retryCount", "2",
                    "请求失败后的重试次数，默认 2。填 0 表示不重试。"),
            new Entry("retryInterval", "3000",
                    "每次重试之间的固定间隔（毫秒），默认 3000。"),
            new Entry("mailEnabled", "false",
                    "签到失败时是否发送邮件通知，默认 false（关闭）。\n"
                            + "改为 true 后需同时填写下面几项。"),
            new Entry("mailHost", "",
                    "SMTP 服务器地址，例如 smtp.qq.com。"),
            new Entry("mailPort", "465",
                    "SMTP 服务器端口，例如 465。"),
            new Entry("mailSsl", "true",
                    "是否使用 SSL 直连。端口 465 填 true；\n"
                            + "填 false 时使用 STARTTLS，服务端不支持则退回明文。"),
            new Entry("mailUsername", "",
                    "SMTP 登录账号。"),
            new Entry("mailPassword", "",
                    "SMTP 登录密码或授权码。多数邮箱需要填授权码，而不是网页登录密码。"),
            new Entry("mailTo", "",
                    "收件人地址，多个收件人用英文逗号分隔。"));

    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static Path loadedPath;

    private AppConfig() {}

    /** 加载配置文件，不存在时生成默认配置；重复调用会重新读取。 */
    public static void load() {
        VALUES.clear();
        loadedPath = null;

        Path path = locate();
        if (path == null) {
            path = createDefault();
        }
        if (path == null) {
            LOG.warn("无法找到或生成配置文件 {}，本次使用内置默认值", FILE_NAME);
            return;
        }

        try {
            parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            appendMissingEntries(path);
            loadedPath = path;
            LOG.info("已加载配置文件: {}", path.toAbsolutePath());
        } catch (IOException e) {
            LOG.error("读取配置文件失败，将使用内置默认值: {} - {}", path, e.getMessage());
            VALUES.clear();
        }
    }

    /** 返回实际使用的配置文件路径，未成功加载则为 null。 */
    public static Path getLoadedPath() {
        return loadedPath;
    }

    public static String getString(String key, String defaultValue) {
        String value = VALUES.get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    public static long getLong(String key, long defaultValue) {
        String value = VALUES.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOG.warn("配置项 {} 的值 \"{}\" 不是合法整数，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = VALUES.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return switch (value.toLowerCase()) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> {
                LOG.warn("配置项 {} 的值 \"{}\" 不是合法布尔值，使用默认值 {}", key, value, defaultValue);
                yield defaultValue;
            }
        };
    }

    private static Path locate() {
        for (Path dir : candidateDirs()) {
            Path file = dir.resolve(FILE_NAME);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    /** 在候选目录中生成默认配置，返回生成的文件；全部失败则返回 null。 */
    private static Path createDefault() {
        for (Path dir : candidateDirs()) {
            Path file = dir.resolve(FILE_NAME);
            try {
                Files.writeString(file, defaultConfigText(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
                LOG.info("未找到配置文件，已生成默认配置: {}", file.toAbsolutePath());
                return file;
            } catch (FileAlreadyExistsException e) {
                return file;
            } catch (IOException e) {
                LOG.warn("无法在 {} 生成配置文件: {}", dir.toAbsolutePath(), e.getMessage());
            }
        }
        return null;
    }

    /** 把文件中尚未出现的已知配置项追加到末尾，让新增参数也能在文件里看到并调整。 */
    private static void appendMissingEntries(Path path) {
        List<Entry> missing = new ArrayList<>();
        for (Entry entry : KNOWN_ENTRIES) {
            if (!VALUES.containsKey(entry.key())) {
                missing.add(entry);
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        StringBuilder content = new StringBuilder();
        for (Entry entry : missing) {
            content.append('\n').append(render(entry));
        }
        try {
            Files.writeString(path, content.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("无法向 {} 追加缺失的配置项，本次改用内置默认值: {}", path, e.getMessage());
            return;
        }

        List<String> keys = missing.stream().map(Entry::key).toList();
        LOG.info("配置文件缺少 {} 个配置项，已追加到末尾: {}", keys.size(), String.join(", ", keys));
        try {
            parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("重新读取配置文件失败，缺失的配置项将使用内置默认值: {}", e.getMessage());
        }
    }

    private static String defaultConfigText() {
        StringBuilder sb = new StringBuilder(FILE_HEADER).append('\n');
        for (Entry entry : KNOWN_ENTRIES) {
            sb.append('\n').append(render(entry));
        }
        return sb.toString();
    }

    private static String render(Entry entry) {
        StringBuilder sb = new StringBuilder();
        for (String line : entry.comment().split("\n")) {
            sb.append("# ").append(line).append('\n');
        }
        return sb.append(entry.key()).append(" = ").append(entry.value()).append('\n').toString();
    }

    /** 优先 jar 所在目录，其次当前工作目录。 */
    private static List<Path> candidateDirs() {
        List<Path> dirs = new ArrayList<>();
        Path jarDir = jarDirectory();
        if (jarDir != null) {
            dirs.add(jarDir);
        }
        dirs.add(Paths.get(System.getProperty("user.dir", ".")));
        return dirs;
    }

    /** 取得 jar 所在目录；从 IDE 或 classes 目录直接运行时返回 null，此时只使用工作目录。 */
    private static Path jarDirectory() {
        try {
            var source = AppConfig.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Paths.get(source.getLocation().toURI());
            return Files.isDirectory(path) ? null : path.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static void parse(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                LOG.warn("{} 第 {} 行缺少 '='，已忽略: {}", FILE_NAME, i + 1, line);
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty()) {
                LOG.warn("{} 第 {} 行的键为空，已忽略: {}", FILE_NAME, i + 1, line);
                continue;
            }
            VALUES.put(key, value);
        }
    }
}
