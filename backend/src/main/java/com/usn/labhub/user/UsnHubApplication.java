package com.usn.labhub.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@MapperScan("com.usn.labhub.user.mapper") // 重点：一定要指向你 Mapper 接口所在的包路径
public class UsnHubApplication {

    public static void main(String[] args) {
        // 在控制台打印一段新的 123456 加密串
        System.out.println(new BCryptPasswordEncoder().encode("123456"));
        SpringApplication.run(UsnHubApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  USN实验室管理系统启动成功   ლ(´ڡ`ლ)ﾞ ");
    }

}