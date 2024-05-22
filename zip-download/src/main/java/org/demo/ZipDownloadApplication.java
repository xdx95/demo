package org.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ZipDownloadApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZipDownloadApplication.class, args);
		log.info("zip-download 服务启动成功!");
	}

}
