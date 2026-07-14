package xyz.denvo;

import xyz.denvo.cli.MainCheckin;
import xyz.denvo.cli.PhoneLogin;
import xyz.denvo.cli.QrcodeLogin;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();
        String[] remaining = shiftArgs(args);

        switch (command) {
            case "checkin":
                long rsaDelay = 0;
                for (int i = 0; i < remaining.length; i++) {
                    if ("--rsa-delay".equals(remaining[i]) && i + 1 < remaining.length) {
                        rsaDelay = Long.parseLong(remaining[++i]);
                    }
                }
                MainCheckin.run(rsaDelay);
                break;
            case "phonelogin":
                PhoneLogin.run(remaining);
                break;
            case "qrcodelogin":
                QrcodeLogin.run(remaining);
                break;
            default:
                System.out.println("未知命令: " + command);
                printUsage();
                System.exit(1);
        }
    }

    private static String[] shiftArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, shifted.length);
        return shifted;
    }

    private static void printUsage() {
        System.out.println("""
            java-kgcheckin 1.0-SNAPSHOT
        
            用法: java -jar kgcheckin-1.0-SNAPSHOT.jar <命令> [参数]
        
            命令:
                checkin [--rsa-delay <ms>]       自动签到领取VIP (遍历users/目录, 可设置RSA延迟毫秒数)
                phoneLogin --phone <手机号>      手机号登录 (发送验证码后通过标准输入输入验证码)
                qrcodeLogin [--number N]        二维码登录 (N为账号数,默认1)
        
            示例:
                java -jar kgcheckin-1.0-SNAPSHOT.jar checkin
                java -jar kgcheckin-1.0-SNAPSHOT.jar checkin --rsa-delay 1200
                java -jar kgcheckin-1.0-SNAPSHOT.jar phoneLogin --phone 12345678910
                java -jar kgcheckin-1.0-SNAPSHOT.jar qrcodeLogin --number 2
        
            登录成功后用户信息自动保存到 users/<userid>.json
            """);
    }
}