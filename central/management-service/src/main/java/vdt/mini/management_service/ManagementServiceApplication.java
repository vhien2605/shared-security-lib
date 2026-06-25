package vdt.mini.management_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ManagementServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ManagementServiceApplication.class, args);
	}
}
