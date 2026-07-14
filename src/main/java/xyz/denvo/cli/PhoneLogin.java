package xyz.denvo.cli;

import xyz.denvo.service.KugouApiService;
import xyz.denvo.util.JsonHelper;
import xyz.denvo.util.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Scanner;

public class PhoneLogin {
    private static final Logger LOG = LoggerFactory.getLogger(PhoneLogin.class);

    @SuppressWarnings("unchecked")
    public static void run(String[] args) throws Exception {
        String phone = null;

        for (int i = 0; i < args.length; i++) {
            if ("--phone".equals(args[i]) && i + 1 < args.length) {
                phone = args[++i];
            }
        }

        if (phone == null || phone.isEmpty()) {
            LOG.error("用法: phoneLogin --phone <手机号>");
            System.exit(1);
            return;
        }

        LOG.warn("开始发送验证码到 {}", phone);
        Map<String, Object> sentResult = KugouApiService.sendCaptcha(phone);

        double sentStatus = sentResult.containsKey("status") ? ((Number) sentResult.get("status")).doubleValue() : 0;
        if (sentStatus != 1) {
            LOG.error("验证码发送失败");
            System.exit(1);
            return;
        }

        System.out.print("请输入验证码: ");
        Scanner scanner = new Scanner(System.in);
        String code = scanner.nextLine().trim();

        if (code.isEmpty()) {
            LOG.error("验证码不能为空");
            System.exit(1);
            return;
        }

        Map<String, Object> result = KugouApiService.loginByPhone(phone, code);

        double status = result.containsKey("status") ? ((Number) result.get("status")).doubleValue() : 0;
        if (status == 1) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data != null) {
                String userid = JsonHelper.safeNumberString(data.get("userid"));
                String token = JsonHelper.safeNumberString(data.get("token"));
                UserStore.saveUser(userid, token);
                LOG.info("登录成功！已保存到 users/{}.json", userid);
            }
        } else {
            double errorCode = result.containsKey("error_code") ? ((Number) result.get("error_code")).doubleValue() : 0;
            if (errorCode == 34175) {
                LOG.error("暂不支持多账号绑定手机登录");
            } else {
                LOG.error("登录失败: {} msg: {}", result.get("error_code"), result.get("msg"));
            }
            System.exit(1);
        }

        System.exit(0);
    }
}
