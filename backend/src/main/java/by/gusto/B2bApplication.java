package by.gusto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class B2bApplication {

    public static void main(String[] args) {
        SpringApplication.run(B2bApplication.class, args);
    }
}
