package pt.hlbk.prompt_cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PromptCacheApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptCacheApplication.class, args);
    }
}
