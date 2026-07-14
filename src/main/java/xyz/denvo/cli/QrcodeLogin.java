package xyz.denvo.cli;

import xyz.denvo.service.KugouApiService;
import xyz.denvo.util.JsonHelper;
import xyz.denvo.util.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class QrcodeLogin {
    private static final Logger LOG = LoggerFactory.getLogger(QrcodeLogin.class);

    @SuppressWarnings("unchecked")
    public static void run(String[] args) throws Exception {
        int number = 1;

        for (int i = 0; i < args.length; i++) {
            if ("--number".equals(args[i]) && i + 1 < args.length) {
                try {
                    number = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    LOG.error("无效的number参数");
                    System.exit(1);
                    return;
                }
            }
        }

        for (int n = 0; n < number; n++) {
            Map<String, Object> keyResult = KugouApiService.getQrKey();
            Map<String, Object> keyData = (Map<String, Object>) keyResult.get("data");

            if (keyData == null) {
                LOG.error("获取二维码数据失败");
                System.exit(1);
                return;
            }

            String qrcode = String.valueOf(keyData.get("qrcode"));
            String qrcodeImg = String.valueOf(keyData.getOrDefault("qrcode_img", ""));

            if (number > 1) {
                LOG.info("=== 第{}/{}个账号 ===", n + 1, number);
            }
            LOG.info("二维码以base64的方式呈现, 请自行解码为图片再使用APP扫描并确认登录");
            if (qrcodeImg != null && !qrcodeImg.isEmpty()) {
                int chunkSize = 1000;
                for (int i = 0; i < qrcodeImg.length(); i += chunkSize) {
                    System.out.println(qrcodeImg.substring(i, Math.min(i + chunkSize, qrcodeImg.length())));
                }
            }

            LOG.info("正在等待，请扫描二维码并确定登录");

            boolean loginSuccess = false;
            for (int i = 0; i < 25; i++) {
                Map<String, Object> checkResult = KugouApiService.checkQrCode(qrcode);
                Map<String, Object> checkData = (Map<String, Object>) checkResult.get("data");

                int qrStatus = 0;
                if (checkData != null && checkData.containsKey("status")) {
                    qrStatus = ((Number) checkData.get("status")).intValue();
                }

                switch (qrStatus) {
                    case 0:
                        LOG.warn("二维码已过期");
                        break;
                    case 4: {
                        String userid = JsonHelper.safeNumberString(checkData.get("userid"));
                        String token = JsonHelper.safeNumberString(checkData.get("token"));
                        UserStore.saveUser(userid, token);
                        LOG.info("登录成功！已保存到 users/{}.json", userid);
                        loginSuccess = true;
                        break;
                    }
                }

                if (qrStatus == 4 || qrStatus == 0) {
                    break;
                }
                if (i == 24) {
                    LOG.error("等待超时");
                    break;
                }
                Thread.sleep(5000);
            }

            if (!loginSuccess && n < number - 1) {
                LOG.error("第{}个账号登录失败", n + 1);
                break;
            }
        }

        System.exit(0);
    }
}