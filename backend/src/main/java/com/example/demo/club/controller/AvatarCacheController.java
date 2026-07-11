package com.example.demo.club.controller;

import com.example.demo.club.service.InstagramAvatarCacheService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/avatars")
public class AvatarCacheController {

    private final InstagramAvatarCacheService instagramAvatarCacheService;

    public AvatarCacheController(InstagramAvatarCacheService instagramAvatarCacheService) {
        this.instagramAvatarCacheService = instagramAvatarCacheService;
    }

    @GetMapping("/instagram/{handle}")
    public ResponseEntity<byte[]> instagramAvatar(@PathVariable String handle) {
        InstagramAvatarCacheService.ResolvedAvatar avatar = instagramAvatarCacheService.resolveAvatar(handle);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(avatar.maxAge(), avatar.maxAgeUnit()).cachePublic())
            .contentType(avatar.mediaType())
            .body(avatar.bytes());
    }
}
