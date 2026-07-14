package xyz.denvo.util;

import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RequestUtil {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public static class Response {
        public int status;
        public String body;
        public List<String> cookies = new ArrayList<>();
        public Map<String, String> responseHeaders = new HashMap<>();
    }

    public static Response request(RequestOptions opts) throws IOException {
        boolean isLite = Config.isLite();
        String dfid = getCookieValue(opts.cookie, "dfid", "-");
        String md5Dfid = CryptoUtil.md5(dfid);
        String mid = md5Dfid + md5Dfid.substring(0, 7);
        String uuid = CryptoUtil.md5(dfid + mid);
        String token = getCookieValue(opts.cookie, "token", "");
        String userid = getCookieValue(opts.cookie, "userid", "0");
        long clienttime = System.currentTimeMillis() / 1000;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dfid", dfid);
        params.put("mid", mid);
        params.put("uuid", uuid);
        params.put("appid", isLite ? Config.LITE_APPID : Config.APPID);
        params.put("clientver", isLite ? Config.LITE_CLIENTVER : Config.CLIENTVER);
        params.put("userid", userid);
        params.put("clienttime", clienttime);

        if (!token.isEmpty()) {
            params.put("token", token);
        }

        if (opts.params != null) {
            params.putAll(opts.params);
        }

        if (opts.encryptKey) {
            params.put("key", SignatureUtil.signKey(
                    String.valueOf(params.getOrDefault("hash", "")),
                    String.valueOf(params.getOrDefault("mid", mid)),
                    String.valueOf(params.getOrDefault("userid", userid)),
                    String.valueOf(params.getOrDefault("appid", isLite ? Config.LITE_APPID : Config.APPID))));
        }

        String dataStr = "";
        if (opts.data != null) {
            dataStr = opts.data;
        }

        if (!params.containsKey("signature") && !opts.notSignature) {
            String encryptType = opts.encryptType != null ? opts.encryptType : "android";
            switch (encryptType) {
                case "register":
                    params.put("signature", SignatureUtil.signatureRegisterParams(params));
                    break;
                case "web":
                    params.put("signature", SignatureUtil.signatureWebParams(params));
                    break;
                case "android":
                default:
                    params.put("signature", SignatureUtil.signatureAndroidParams(params, dataStr));
                    break;
            }
        }

        String baseUrl = opts.baseURL != null ? opts.baseURL : Config.KUGOU_GATEWAY;
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl + opts.url)).newBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
        }
        HttpUrl url = urlBuilder.build();

        Request.Builder reqBuilder = new Request.Builder().url(url);
        String method = opts.method != null ? opts.method.toUpperCase() : "GET";
        reqBuilder.header("User-Agent", Config.USER_AGENT);
        reqBuilder.header("dfid", dfid);
        reqBuilder.header("clienttime", String.valueOf(clienttime));
        reqBuilder.header("mid", mid);

        if (opts.headers != null) {
            for (Map.Entry<String, String> entry : opts.headers.entrySet()) {
                reqBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        if ("POST".equals(method) && !dataStr.isEmpty()) {
            reqBuilder.post(RequestBody.create(dataStr, MediaType.get("application/json; charset=utf-8")));
        } else if ("POST".equals(method)) {
            reqBuilder.post(RequestBody.create("", null));
        }

        try (okhttp3.Response response = CLIENT.newCall(reqBuilder.build()).execute()) {
            Response resp = new Response();
            resp.body = response.body() != null ? response.body().string() : "";
            resp.status = response.code();

            List<String> setCookies = response.headers("Set-Cookie");
            for (String c : setCookies) {
                String parsed = c.replaceAll("\\s*(Domain|domain|path|expires)=[^(;|$)]+;*", "")
                        .replace(";HttpOnly", "");
                resp.cookies.add(parsed);
            }

            for (String name : response.headers().names()) {
                resp.responseHeaders.put(name, response.headers().get(name));
            }

            if (response.isSuccessful()) {
                resp.status = 200;
            }

            return resp;
        }
    }

    private static String getCookieValue(Map<String, String> cookies, String key, String defaultValue) {
        if (cookies == null) return defaultValue;
        return cookies.getOrDefault(key, defaultValue);
    }

    public static class RequestOptions {
        public String method = "GET";
        public String url;
        public String baseURL;
        public Map<String, Object> params;
        public String data;
        public Map<String, String> headers;
        public Map<String, String> cookie;
        public String encryptType = "android";
        public boolean encryptKey;
        public boolean notSignature;
        public String ip;
    }
}