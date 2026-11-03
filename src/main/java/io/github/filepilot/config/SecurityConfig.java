package io.github.filepilot.config;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean org.springframework.security.core.userdetails.UserDetailsService users() {
        return new org.springframework.security.provisioning.InMemoryUserDetailsManager();
    }
    @Bean SecurityFilterChain security(HttpSecurity http, Environment env) throws Exception {
        if (!"127.0.0.1".equals(env.getProperty("server.address")))
            throw new IllegalStateException("FilePilot requires server.address=127.0.0.1");
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .formLogin(f -> f.disable()).httpBasic(b -> b.disable())
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'")))
            .addFilterBefore(new LocalOriginFilter(), CsrfFilter.class).build();
    }

    static final class LocalOriginFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String host = req.getServerName();
            boolean local = Set.of("127.0.0.1", "localhost").contains(host);
            String origin = req.getHeader("Origin");
            String expected = req.getScheme() + "://" + host + (req.getServerPort() == 80 ? "" : ":" + req.getServerPort());
            if (!local || (origin != null && !origin.equals(expected)) || "cross-site".equals(req.getHeader("Sec-Fetch-Site"))) {
                res.sendError(403); return;
            }
            chain.doFilter(req, res);
        }
    }
}
