package com.trading.paper_trade.shell;

// Spring Core & Context Imports
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Shell Specific Imports
import org.springframework.shell.jline.PromptProvider;

// JLine (The terminal library used by Spring Shell)
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

@Configuration
public class ShellConfig {
    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("trading-bot:> ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }
}