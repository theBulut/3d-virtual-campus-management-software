package de.tudarmstadt.campus.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CampusAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAdminApplication.class, args);
    }
}
