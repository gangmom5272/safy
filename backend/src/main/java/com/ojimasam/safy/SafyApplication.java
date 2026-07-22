package com.ojimasam.safy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SafyApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafyApplication.class, args);
	}

}
