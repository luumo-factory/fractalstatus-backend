package ai.luumo.fractalstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FractalstatusProperties.class)
public class FractalstatusApplication {

	public static void main(String[] args) {
		SpringApplication.run(FractalstatusApplication.class, args);
	}

}
