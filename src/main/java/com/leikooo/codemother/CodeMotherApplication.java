package com.leikooo.codemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author leikooo
 */
@SpringBootApplication
@MapperScan("com.leikooo.codemother.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class CodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeMotherApplication.class, args);
    }

}
