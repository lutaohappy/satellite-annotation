package com.annotation.satelliteannotationbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.annotation.satelliteannotationbackend", "com.annotation.mcpserver"})
public class SatelliteAnnotationBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SatelliteAnnotationBackendApplication.class, args);
    }
}
