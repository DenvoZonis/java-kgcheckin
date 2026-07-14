package xyz.denvo.cli;

import xyz.denvo.service.KugouApiService;
import xyz.denvo.util.Config;
import xyz.denvo.util.JsonHelper;
import xyz.denvo.util.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainCheckin {

    private static final Logger LOG = LoggerFactory.getLogger(MainCheckin.class);
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @SuppressWarnings("unchecked")
    public static void run(long rsaDelayMs) throws Exception {
        Config.RSA_delay_ms = rsaDelayMs;
        List<Map<String, Object>> users = UserStore.loadAllUsers();
        if (users.isEmpty()) {
            LOG.error("users目录中没有用户文件，请先运行 phoneLogin 或 qrcodeLogin 登录");
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

            Map<String, Object> detail = KugouApiService.getUserDetail(token, userid);
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
            Map<String, Object> listen = KugouApiService.reportListenSong(token, userid);
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
                Map<String, Object> ad = KugouApiService.reportAdPlay(token, userid);
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

            Map<String, Object> vip = KugouApiService.getVipDetail(token, userid);
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
            System.out.println(JsonHelper.GSON.toJson(errorMsg));
            System.exit(1);
        }

        System.exit(0);
    }
}