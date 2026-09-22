package xyz.denvo.cli;

import xyz.denvo.service.KugouApiService;
import xyz.denvo.service.MailService;
import xyz.denvo.util.AppConfig;
import xyz.denvo.util.Config;
import xyz.denvo.util.JsonHelper;
import xyz.denvo.util.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

public class MainCheckin {

    private static final Logger LOG = LoggerFactory.getLogger(MainCheckin.class);
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final String MAIL_SUBJECT = "[kgcheckin] 签到失败，需要人工处理";

    private static int retryCount;
    private static long retryIntervalMs;

    /** 执行过程中任何未捕获的异常都会在这里补发通知邮件，之后原样抛出。 */
    public static void run(long rsaDelayMs) throws Exception {
        try {
            checkin(rsaDelayMs);
        } catch (Exception e) {
            LOG.error("签到过程中断: {}", e.toString());
            notifyFailure("执行过程中断", e.toString());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkin(long rsaDelayMs) throws Exception {
        Config.RSA_delay_ms = rsaDelayMs;
        retryCount = (int) Math.max(0, AppConfig.getLong("retryCount", 2));
        retryIntervalMs = Math.max(0, AppConfig.getLong("retryInterval", 3000));

        List<Map<String, Object>> users = UserStore.loadAllUsers();
        if (users.isEmpty()) {
            LOG.error("users目录中没有用户文件，请先运行 phoneLogin 或 qrcodeLogin 登录");
            notifyFailure("users目录中没有用户文件，请先运行 phoneLogin 或 qrcodeLogin 登录", null);
            System.exit(1);
            return;
        }
        LOG.info("共加载 {} 个用户", users.size());

        ZonedDateTime beijingNow = ZonedDateTime.now(BEIJING);
        boolean isSunday = beijingNow.getDayOfWeek().getValue() == 7;
        String dateStr = beijingNow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        boolean needRefresh = false;

        Map<String, Object> errorMsg = new LinkedHashMap<>();

        for (Map<String, Object> user : users) {
            String userid = JsonHelper.safeNumberString(user.get("userid"));
            String token = JsonHelper.safeNumberString(user.get("token"));

            Map<String, Object> detail = requestWithRetry("获取账号信息",
                    () -> KugouApiService.getUserDetail(token, userid),
                    MainCheckin::hasUserDetail);
            Map<String, Object> detailData = (Map<String, Object>) detail.get("data");

            if (detailData == null || detailData.get("nickname") == null) {
                LOG.error("token过期或账号不存在, userid: {}", userid);
                LOG.error("如果上面两个日志的时间间隔较长（比如2s），请尝试调整RSA延迟，详见项目仓库Readme文件");
                errorMsg.put("userid_" + userid, Map.of("msg", "token过期或账号不存在"));
                continue;
            }

            String nickname = String.valueOf(detailData.get("nickname"));
            LOG.info("账号 {} 开始领取VIP...", nickname);

            if (isSunday) {
                Map<String, Object> refresh = KugouApiService.refreshToken(token, userid);
                Number refreshStatus = (Number) refresh.get("status");
                if (refreshStatus != null && refreshStatus.intValue() == 1) {
                    Map<String, Object> refreshData = (Map<String, Object>) refresh.get("data");
                    if (refreshData != null) {
                        String newToken = JsonHelper.safeNumberString(refreshData.get("token"));
                        if (!newToken.equals(token)) {
                            needRefresh = true;
                            LOG.warn("账号 {} 需要刷新token", nickname);
                            user.put("token", newToken);
                            UserStore.saveUser(user);
                        }
                    }
                }
            }

            LOG.warn("开始听歌领取VIP...");
            Map<String, Object> listen = requestWithRetry("听歌领取",
                    () -> KugouApiService.reportListenSong(token, userid),
                    res -> isSuccess(res) || isErrorCode(res, 130012));
            Number listenStatus = (Number) listen.get("status");
            Number listenCode = (Number) listen.get("error_code");
            if (listenStatus != null && listenStatus.intValue() == 1) {
                LOG.info("听歌领取成功");
            } else if (listenCode != null && listenCode.intValue() == 130012) {
                LOG.info("今日已领取");
            } else {
                LOG.error("听歌领取失败");
                errorMsg.put(nickname + "_listen", listen);
            }

            LOG.warn("开始领取VIP...");
            for (int i = 1; i <= 8; i++) {
                Map<String, Object> ad = requestWithRetry("广告领取",
                        () -> KugouApiService.reportAdPlay(token, userid),
                        res -> isSuccess(res) || isErrorCode(res, 30002));
                Number adStatus = (Number) ad.get("status");
                Number adCode = (Number) ad.get("error_code");
                if (adStatus != null && adStatus.intValue() == 1) {
                    LOG.info("第{}次领取成功", i);
                    if (i != 8) {
                        Thread.sleep(30 * 1000);
                    }
                } else if (adCode != null && adCode.intValue() == 30002) {
                    LOG.info("今天次数已用光");
                    break;
                } else {
                    LOG.error("第{}次领取失败", i);
                    errorMsg.put(nickname + "_ad", ad);
                    break;
                }
            }

            Map<String, Object> vip = requestWithRetry("查询VIP",
                    () -> KugouApiService.getVipDetail(token, userid),
                    MainCheckin::isSuccess);
            Number vipStatus = (Number) vip.get("status");
            if (vipStatus != null && vipStatus.intValue() == 1) {
                Map<String, Object> vipData = (Map<String, Object>) vip.get("data");
                if (vipData != null) {
                    List<Map<String, Object>> busiVip = (List<Map<String, Object>>) vipData.get("busi_vip");
                    if (busiVip != null && !busiVip.isEmpty()) {
                        LOG.info("今天是：{}", dateStr);
                        LOG.info("VIP到期时间：{}", busiVip.getFirst().getOrDefault("vip_end_time", "未知"));
                    }
                }
            } else {
                LOG.error("获取失败");
                errorMsg.put(nickname + "_vip_details", vip);
            }
            System.out.println();
        }

        if (needRefresh) {
            LOG.info("token已自动刷新并保存到users目录");
        }

        if (!errorMsg.isEmpty()) {
            LOG.error("异常信息如下:");
            String detail = JsonHelper.GSON.toJson(errorMsg);
            System.out.println(detail);
            notifyFailure("有账号签到失败", detail);
            System.exit(1);
        }

        System.exit(0);
    }

    /** 发送失败通知邮件，未启用邮件通知时是空操作。 */
    private static void notifyFailure(String reason, String detail) {
        String now = ZonedDateTime.now(BEIJING).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder body = new StringBuilder()
                .append("时间: ").append(now)
                .append("\n原因: ").append(reason);
        if (detail != null) {
            body.append("\n\n").append(detail);
        }
        MailService.send(MAIL_SUBJECT, body.toString());
    }

    /** 可重试的请求。 */
    @FunctionalInterface
    private interface Request {
        Map<String, Object> call() throws IOException;
    }

    /**
     * 执行请求，失败时按配置的次数与固定间隔重试。
     * <p>
     * 失败指返回体不满足 {@code ok}，或抛出 {@link IOException}。重试耗尽后返回最后一次的返回体，
     * 交给调用方原有的错误分支处理；若最后一次仍是 {@link IOException}，则原样抛出。
     */
    private static Map<String, Object> requestWithRetry(String label, Request request,
                                                        Predicate<Map<String, Object>> ok)
            throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            Map<String, Object> result = null;
            IOException failure = null;
            try {
                result = request.call();
                if (ok.test(result)) {
                    return result;
                }
            } catch (IOException e) {
                failure = e;
            }

            if (attempt >= retryCount) {
                if (failure != null) {
                    throw failure;
                }
                return result;
            }

            LOG.warn("{}失败{}，{}ms 后进行第 {}/{} 次重试", label,
                    failure != null ? "（" + failure.getMessage() + "）" : "",
                    retryIntervalMs, attempt + 1, retryCount);
            Thread.sleep(retryIntervalMs);
        }
    }

    private static boolean isSuccess(Map<String, Object> res) {
        Number status = (Number) res.get("status");
        return status != null && status.intValue() == 1;
    }

    private static boolean isErrorCode(Map<String, Object> res, int code) {
        Number errorCode = (Number) res.get("error_code");
        return errorCode != null && errorCode.intValue() == code;
    }

    /** token 有效时才会返回 nickname。 */
    @SuppressWarnings("unchecked")
    private static boolean hasUserDetail(Map<String, Object> res) {
        Map<String, Object> data = (Map<String, Object>) res.get("data");
        return data != null && data.get("nickname") != null;
    }
}
