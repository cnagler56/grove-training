package com.grove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GroveServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GroveServerApplication.class, args);
		System.out.println("..........................Grove Training running........................");
	}

}
