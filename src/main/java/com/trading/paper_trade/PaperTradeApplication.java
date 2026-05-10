package com.trading.paper_trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PaperTradeApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaperTradeApplication.class, args);
	}

}
