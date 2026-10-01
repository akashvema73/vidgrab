package com.vidgrab.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "download_history")
@Getter @Setter
public class DownloadHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platform_id")
    private Platform platform;

    @Column(nullable = false, length = 1000)
    private String videoUrl;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String thumbnail;

    private String uploader;
    private Long durationSeconds;

    @Column(nullable = false, length = 20)
    private String quality;

    @Column(length = 500)
    private String fileName;

    @Column(nullable = false, length = 10)
    private String status = "SUCCESS";

    @Column(length = 500)
    private String errorMessage;

    @Column(length = 45)
    private String clientIp;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
