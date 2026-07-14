package xyz.denvo.service;

import xyz.denvo.util.*;
import xyz.denvo.util.CryptoUtil.AesResult;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public final class KugouApiService {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final String LOGIN_TOKEN_KEY = "90b8382a1bb4ccdcf063102053fd75b8";
    private static final String LOGIN_TOKEN_IV = "f063102053fd75b8";
    private static final String LITE_LOGIN_TOKEN_KEY = "c24f74ca2820225badc01946dba4fdf7";
    private static final String LITE_LOGIN_TOKEN_IV = "adc01946dba4fdf7";

    private KugouApiService() {}

    // --- 验证码 ---
    public static Map<String, Object> sendCaptcha(String mobile) throws IOException {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("businessid", 5);
        dataMap.put("mobile", mobile);
        dataMap.put("plat", 3);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = "http://login.user.kugou.com";
        opts.url = "/v7/send_mobile_code";
        opts.data = JsonHelper.toJson(dataMap);
        opts.encryptType = "android";
        opts.cookie = new HashMap<>();

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    // --- 手机号登录 ---
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loginByPhone(String mobile, String code) throws IOException {
        boolean isLite = Config.isLite();
        long dateTime = System.currentTimeMillis();

        Map<String, Object> encryptData = new LinkedHashMap<>();
        encryptData.put("mobile", mobile);
        encryptData.put("code", code);
        AesResult encrypt = CryptoUtil.aesEncrypt(JsonHelper.toJson(encryptData));

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("plat", 1);
        dataMap.put("support_multi", 1);
        dataMap.put("t1", 0);
        dataMap.put("t2", 0);
        dataMap.put("clienttime_ms", dateTime);
        dataMap.put("mobile", mobile);
        dataMap.put("key", SignatureUtil.signParamsKey(dateTime));

        if (isLite) {
            Map<String, Object> p2Data = new LinkedHashMap<>();
            p2Data.put("clienttime_ms", dateTime);
            p2Data.put("code", code);
            p2Data.put("mobile", mobile);
            dataMap.put("p2", CryptoUtil.rsaEncrypt(JsonHelper.toJson(p2Data)).toUpperCase());
        } else {
            String masked = mobile.length() >= 11
                    ? mobile.substring(0, 2) + "*****" + mobile.substring(10, 11)
                    : mobile;
            dataMap.put("mobile", masked);
            dataMap.put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
            dataMap.put("params", encrypt.str());
            Map<String, Object> pkData = new LinkedHashMap<>();
            pkData.put("clienttime_ms", dateTime);
            pkData.put("key", encrypt.key());
            dataMap.put("pk", CryptoUtil.rsaEncrypt(JsonHelper.toJson(pkData)).toUpperCase());
        }

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = Config.KUGOU_GATEWAY;
        opts.url = "/" + (isLite ? "v6" : "v7") + "/login_by_verifycode";
        opts.data = JsonHelper.toJson(dataMap);
        opts.encryptType = "android";
        opts.headers = Map.of("x-router", "login.user.kugou.com");
        opts.cookie = new HashMap<>();

        RequestUtil.Response res = RequestUtil.request(opts);
        Map<String, Object> body = JsonHelper.GSON.fromJson(res.body, MAP_TYPE);

        if (body != null && Integer.valueOf(1).equals(((Number) body.getOrDefault("status", 0)).intValue())) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data != null && data.containsKey("secu_params")) {
                String secuParams = String.valueOf(data.get("secu_params"));
                try {
                    String token = CryptoUtil.aesDecrypt(secuParams, encrypt.key());
                    Map<String, Object> tokenData = JsonHelper.GSON.fromJson(token, MAP_TYPE);
                    if (tokenData != null) {
                        data.putAll(tokenData);
                    }
                } catch (Exception e) {
                    data.put("token", CryptoUtil.aesDecrypt(secuParams, encrypt.key()));
                }
            }
            body.put("data", data);
        }
        return body;
    }

    // --- 二维码 ---
    public static Map<String, Object> getQrKey() throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appid", 1001);
        params.put("type", 1);
        params.put("plat", 4);
        params.put("qrcode_txt", "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + Config.APPID + "&");
        params.put("srcappid", Config.SRCAPPID);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "GET";
        opts.baseURL = "https://login-user.kugou.com";
        opts.url = "/v2/qrcode";
        opts.params = params;
        opts.encryptType = "web";
        opts.cookie = new HashMap<>();

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> checkQrCode(String qrcode) throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plat", 4);
        params.put("appid", Config.APPID);
        params.put("srcappid", Config.SRCAPPID);
        params.put("qrcode", qrcode);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "GET";
        opts.baseURL = "https://login-user.kugou.com";
        opts.url = "/v2/get_userinfo_qrcode";
        opts.params = params;
        opts.encryptType = "web";
        opts.cookie = new HashMap<>();

        RequestUtil.Response res = RequestUtil.request(opts);
        Map<String, Object> body = JsonHelper.GSON.fromJson(res.body, MAP_TYPE);

        if (body != null) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data != null && Integer.valueOf(4).equals(((Number) data.getOrDefault("status", 0)).intValue())) {
                data.put("cookies", res.cookies);
            }
        }
        return body;
    }

    // --- 用户详情（验证token） ---
    public static Map<String, Object> getUserDetail(String token, String userid) throws IOException {
        long clienttime = System.currentTimeMillis() / 1000;

        Map<String, Object> pkData = new LinkedHashMap<>();
        pkData.put("token", token);
        pkData.put("clienttime", clienttime);
        String pk = CryptoUtil.rsaEncrypt(JsonHelper.toJson(pkData)).toUpperCase();

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("visit_time", clienttime);
        dataMap.put("usertype", 1);
        dataMap.put("p", pk);
        dataMap.put("userid", Long.parseLong(userid));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("plat", 1);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = Config.KUGOU_GATEWAY;
        opts.url = "/v3/get_my_info";
        opts.data = JsonHelper.toJson(dataMap);
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = buildCookie(token, userid);
        opts.headers = Map.of("x-router", "usercenter.kugou.com");

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    // --- 刷新token ---
    @SuppressWarnings("unchecked")
    public static Map<String, Object> refreshToken(String token, String userid) throws IOException {
        boolean isLite = Config.isLite();
        long dateNow = System.currentTimeMillis();

        Map<String, Object> tokenEncData = new LinkedHashMap<>();
        tokenEncData.put("clienttime", dateNow / 1000);
        tokenEncData.put("token", token);
        String p3 = CryptoUtil.aesEncryptWithKeyIv(JsonHelper.toJson(tokenEncData),
                isLite ? LITE_LOGIN_TOKEN_KEY : LOGIN_TOKEN_KEY,
                isLite ? LITE_LOGIN_TOKEN_IV : LOGIN_TOKEN_IV);

        AesResult encryptParams = CryptoUtil.aesEncrypt("{}");

        Map<String, Object> pkData = new LinkedHashMap<>();
        pkData.put("clienttime_ms", dateNow);
        pkData.put("key", encryptParams.key());
        String pk = CryptoUtil.rsaEncrypt(JsonHelper.toJson(pkData)).toUpperCase();

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("dfid", "-");
        dataMap.put("p3", p3);
        dataMap.put("plat", 1);
        dataMap.put("t1", 0);
        dataMap.put("t2", 0);
        dataMap.put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
        dataMap.put("pk", pk);
        dataMap.put("params", encryptParams.str());
        dataMap.put("userid", Long.parseLong(userid));
        dataMap.put("clienttime_ms", dateNow);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = "http://login.user.kugou.com";
        opts.url = "/" + (isLite ? "v4" : "v5") + "/login_by_token";
        opts.data = JsonHelper.toJson(dataMap);
        opts.cookie = buildCookie(token, userid);
        opts.encryptType = "android";
        opts.headers = Map.of("x-router", "login.user.kugou.com");

        RequestUtil.Response res = RequestUtil.request(opts);
        Map<String, Object> body = JsonHelper.GSON.fromJson(res.body, MAP_TYPE);

        if (body != null) {
            Number status = (Number) body.get("status");
            if (status != null && status.intValue() == 1) {
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.containsKey("secu_params")) {
                    String secuParams = String.valueOf(data.get("secu_params"));
                    String decrypted = CryptoUtil.aesDecrypt(secuParams, encryptParams.key());
                    try {
                        Map<String, Object> tokenData = JsonHelper.GSON.fromJson(decrypted, MAP_TYPE);
                        if (tokenData != null) {
                            data.putAll(tokenData);
                        }
                    } catch (Exception e) {
                        data.put("token", decrypted);
                    }
                }
                body.put("data", data);
            }
        }
        return body;
    }

    // --- 听歌领VIP ---
    public static Map<String, Object> reportListenSong(String token, String userid) throws IOException {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("mixsongid", 666075191L);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("clientver", 10566);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = Config.KUGOU_GATEWAY;
        opts.url = "/youth/v2/report/listen_song";
        opts.data = JsonHelper.toJson(dataMap);
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = buildCookie(token, userid);
        opts.headers = new HashMap<>();
        opts.headers.put("user-agent", "Android13-1070-10566-201-0-ReportPlaySongToServerProtocol-wifi");
        opts.headers.put("content-type", "application/json; charset=utf-8");

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    // --- 广告领VIP ---
    public static Map<String, Object> reportAdPlay(String token, String userid) throws IOException {
        long time = System.currentTimeMillis();

        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("ad_id", 12307537187L);
        dataMap.put("play_end", time);
        dataMap.put("play_start", time - 30000);

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "POST";
        opts.baseURL = Config.KUGOU_GATEWAY;
        opts.url = "/youth/v1/ad/play_report";
        opts.data = JsonHelper.toJson(dataMap);
        opts.encryptType = "android";
        opts.cookie = buildCookie(token, userid);

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    // --- 查询VIP ---
    public static Map<String, Object> getVipDetail(String token, String userid) throws IOException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("busi_type", "concept");

        RequestUtil.RequestOptions opts = new RequestUtil.RequestOptions();
        opts.method = "GET";
        opts.baseURL = "https://kugouvip.kugou.com";
        opts.url = "/v1/get_union_vip";
        opts.params = params;
        opts.encryptType = "android";
        opts.cookie = buildCookie(token, userid);

        RequestUtil.Response res = RequestUtil.request(opts);
        return parseBody(res.body);
    }

    // --- 辅助方法 ---
    private static Map<String, String> buildCookie(String token, String userid) {
        Map<String, String> cookie = new HashMap<>();
        cookie.put("token", token);
        cookie.put("userid", userid);
        return cookie;
    }

    private static Map<String, Object> parseBody(String body) {
        Map<String, Object> result = JsonHelper.GSON.fromJson(body, MAP_TYPE);
        return result != null ? result : new LinkedHashMap<>();
    }
}