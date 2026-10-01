package com.vidgrab.config;

import com.vidgrab.entity.Platform;
import com.vidgrab.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlatformRepository repo;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;
        repo.save(new Platform(null, "YouTube", "youtube.com,youtu.be,music.youtube.com", true));
        repo.save(new Platform(null, "Instagram", "instagram.com", true));
        repo.save(new Platform(null, "Facebook", "facebook.com,fb.watch,fb.com", true));
    }
}
