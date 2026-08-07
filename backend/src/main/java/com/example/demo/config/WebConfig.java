package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + absolutePath + "/");
    }

    // Standard hardening for user-uploaded files served from our own origin.
    //
    // X-Content-Type-Options stops the browser from trying to sniff a malicious upload's
    // content type. Spring Security's default header writer already adds this for every
    // response, but it is set explicitly here too so it does not silently disappear if that
    // default is ever narrowed or disabled.
    //
    // X-Robots-Tag keeps uploaded photos out of search indexes. robots.txt cannot do that job
    // here: it is served from the SPA origin, while these files are served from the API origin,
    // so it has no authority over them. The noindex robots meta tag ClubMediaView sets covers
    // the HTML page but not a direct hit on the image URL. A response header is origin-
    // independent and covers both.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Robots-Tag", "noindex");
                return true;
            }
        }).addPathPatterns("/uploads/**");
    }
}
