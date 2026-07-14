package xyz.denvo.util;

import java.util.concurrent.ThreadLocalRandom;

public final class Config {
    private Config() {}

    public static long RSA_delay_ms = 0;

    public static final int APPID = 1005;
    public static final int LITE_APPID = 3116;
    public static final int CLIENTVER = 20489;
    public static final int LITE_CLIENTVER = 11436;
    public static final int SRCAPPID = 2919;

    public static final String ANDROID_SECRET = "OIlwieks28dk2k092lksi2UIkp";
    public static final String LITE_ANDROID_SECRET = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";
    public static final String WEB_SECRET = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    public static final String REGISTER_SECRET = "1014";
    public static final String SIGN_KEY_SECRET = "57ae12eb6890223e355ccfcb74edf70d";
    public static final String LITE_SIGN_KEY_SECRET = "185672dd44712f60bb1736df5a377e82";

    public static final String PUBLIC_RSA_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/g
            bjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+
            UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE
            5E221wf/4WLFxwAtRQIDAQAB
            -----END PUBLIC KEY-----""";

    public static final String PUBLIC_LITE_RSA_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO0
            1rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTL
            XgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ0
            8/q6ifSioBszvb3QiwIDAQAB
            -----END PUBLIC KEY-----""";

    public static final String USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";
    public static final String KUGOU_GATEWAY = "https://gateway.kugou.com";

    public static boolean isLite() {
        String platform = System.getenv("platform");
        return "lite".equals(platform);
    }

    public static int getAppid() {
        return isLite() ? LITE_APPID : APPID;
    }

    public static int getClientver() {
        return isLite() ? LITE_CLIENTVER : CLIENTVER;
    }

    public static String getAndroidSecret() {
        return isLite() ? LITE_ANDROID_SECRET : ANDROID_SECRET;
    }

    public static String getPublicRsaKey() {
        return isLite() ? PUBLIC_LITE_RSA_KEY : PUBLIC_RSA_KEY;
    }

    public static String getSignKeySecret() {
        return isLite() ? LITE_SIGN_KEY_SECRET : SIGN_KEY_SECRET;
    }

    public static String randomString(int len) {
        String chars = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}