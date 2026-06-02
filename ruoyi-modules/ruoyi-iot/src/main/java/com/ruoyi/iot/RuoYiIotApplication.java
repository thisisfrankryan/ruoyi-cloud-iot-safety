package com.ruoyi.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;

/**
 * 物联网数据水域安全报警微服务启动入口
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class RuoYiIotApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuoYiIotApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  物联网水域安全监控模块启动成功   ლ(´ڡ`ლ)ﾞ \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _ _/  /       \n" +
                " |(_ o _) /        \\      /        \n" +
                " | (_,_).' __       \\    /         \n" +
                " |  |\\ \\  |  |       \\  /          \n" +
                " |  | \\ `'   /        \\/           \n" +
                " |  |  \\    /                      \n" +
                " ''-'   `'-'                       ");
    }
}
