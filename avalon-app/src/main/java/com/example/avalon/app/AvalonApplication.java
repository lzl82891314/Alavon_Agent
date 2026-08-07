package com.example.avalon.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication(scanBasePackages = "com.example.avalon")
@EnableJpaRepositories(basePackages = "com.example.avalon.persistence.repository")
@EntityScan(basePackages = "com.example.avalon.persistence.entity")
public class AvalonApplication {
    public static void main(String[] args) {
        ensureDefaultArenaDirectory();
        org.springframework.boot.SpringApplication.run(AvalonApplication.class, AvalonLaunchMode.resolve(args).launchArgs(args));
    }

    private static void ensureDefaultArenaDirectory() {
        try {
            Files.createDirectories(Path.of(System.getProperty("user.home"), ".avalon-agent", "arenas", "default--37a8eec1"));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create the default Avalon arena directory", exception);
        }
    }
}
