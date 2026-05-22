package com.trading.paper_trade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Locks the REST API behind HTTP Basic auth. Previously the {@code /trade/*}
 * endpoints were wide open, so anyone able to reach the port could place orders.
 *
 * <p>The API is stateless (no session) and CSRF protection is disabled, which is
 * appropriate for a non-browser Basic-auth API. The interactive shell is not
 * served over HTTP and is unaffected by this configuration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RestAuthenticationEntryPoint authEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authEntryPoint))
                // covers the "no credentials at all" path (access denied before auth runs)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(TradeApiProperties props, PasswordEncoder encoder) {
        UserDetails trader = User.withUsername(props.username())
                .password(encoder.encode(props.password()))
                .roles("TRADER")
                .build();
        return new InMemoryUserDetailsManager(trader);
    }
}
