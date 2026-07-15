package com.worldcup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /worldcup (bez ukosnika) -> /worldcup/ zeby wzgledne sciezki zasobow dzialaly
        registry.addRedirectViewController("/worldcup", "/worldcup/");
        // resource handler nie serwuje index.html z podkatalogow - jawny forward
        registry.addViewController("/worldcup/").setViewName("forward:/worldcup/index.html");
        // /account to widok SPA landinga (bez ukosnika, zeby wzgledne sciezki
        // zasobow w index.html rozwiazywaly sie do korzenia)
        registry.addRedirectViewController("/account/", "/account");
        registry.addViewController("/account").setViewName("forward:/index.html");
    }
}
