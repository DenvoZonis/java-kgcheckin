package xyz.denvo.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SignatureUtil {
    private SignatureUtil() {}

    public static String signatureAndroidParams(Map<String, Object> params, String data) {
        String secret = Config.getAndroidSecret();
        String sorted = sortedKeyValueString(params);
        return CryptoUtil.md5(secret + sorted + (data != null ? data : "") + secret);
    }

    public static String signatureWebParams(Map<String, Object> params) {
        String secret = Config.WEB_SECRET;
        String sorted = sortedKeyValueString(params);
        return CryptoUtil.md5(secret + sorted + secret);
    }

    public static String signatureRegisterParams(Map<String, Object> params) {
        String secret = Config.REGISTER_SECRET;
        List<String> values = new ArrayList<>();
        for (Object v : params.values()) {
            values.add(String.valueOf(v));
        }
        Collections.sort(values);
        String joined = String.join("", values);
        return CryptoUtil.md5(secret + joined + secret);
    }

    public static String signKey(String hash, String mid, String userid, String appid) {
        String secret = Config.getSignKeySecret();
        return CryptoUtil.md5(hash + secret + appid + mid + (userid != null ? userid : "0"));
    }

    public static String signParamsKey(long dateTime) {
        return signParamsKey(dateTime, Config.getAppid(), Config.getClientver());
    }

    public static String signParamsKey(long dateTime, int appid, int clientver) {
        String secret = Config.getAndroidSecret();
        return CryptoUtil.md5(appid + secret + clientver + dateTime);
    }

    private static String sortedKeyValueString(Map<String, Object> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            Object value = params.get(key);
            String strValue;
            if (value instanceof Map || value instanceof List) {
                strValue = JsonHelper.toJson(value);
            } else {
                strValue = String.valueOf(value);
            }
            sb.append(key).append("=").append(strValue);
        }
        return sb.toString();
    }
}