package xyz.denvo.util;

import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class UserStore {
    private static final Logger LOG = LoggerFactory.getLogger(UserStore.class);
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static Path usersDir;

    private UserStore() {}

    private static Path getUsersDir() {
        if (usersDir == null) {
            String jarDir = System.getProperty("user.dir");
            usersDir = Paths.get(jarDir, "users");
            try {
                Files.createDirectories(usersDir);
            } catch (IOException e) {
                throw new RuntimeException("无法创建users目录: " + usersDir, e);
            }
        }
        return usersDir;
    }

    public static List<Map<String, Object>> loadAllUsers() throws IOException {
        List<Map<String, Object>> users = new ArrayList<>();
        Path dir = getUsersDir();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    Map<String, Object> user = JsonHelper.GSON.fromJson(content, MAP_TYPE);
                    if (user != null && user.containsKey("userid") && user.containsKey("token")) {
                        users.add(user);
                    }
                } catch (Exception e) {
                    LOG.error("读取用户文件失败: {} - {}", file, e.getMessage());
                }
            }
        }
        return users;
    }

    public static void saveUser(String userid, String token) throws IOException {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userid", userid);
        user.put("token", token);

        Path file = getUsersDir().resolve(userid + ".json");
        Files.writeString(file, JsonHelper.GSON.toJson(user), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void saveUser(Map<String, Object> user) throws IOException {
        String userid = JsonHelper.safeNumberString(user.get("userid"));
        String token = JsonHelper.safeNumberString(user.get("token"));
        saveUser(userid, token);
    }
}