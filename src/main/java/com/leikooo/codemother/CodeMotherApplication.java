package com.leikooo.codemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author leikooo
 */
@SpringBootApplication
@MapperScan("com.leikooo.codemother.mapper")
public class CodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeMotherApplication.class, args);
    }

}
