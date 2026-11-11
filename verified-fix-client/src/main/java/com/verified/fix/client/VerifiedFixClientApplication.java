package com.verified.fix.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Starts the FIX client: connects to the FIX server and offers a small local REST API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VerifiedFixClientApplication {
    /** Starts the client application. */
    public static void main(String[] args) {
        SpringApplication.run(VerifiedFixClientApplication.class, args);
    }
}
