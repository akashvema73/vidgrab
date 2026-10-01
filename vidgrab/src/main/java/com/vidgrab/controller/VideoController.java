package com.vidgrab.controller;

import com.vidgrab.entity.DownloadHistory;
import com.vidgrab.entity.Feedback;
import com.vidgrab.entity.Platform;
import com.vidgrab.repository.DownloadHistoryRepository;
import com.vidgrab.repository.FeedbackRepository;
import com.vidgrab.repository.PlatformRepository;
import com.vidgrab.service.YtDlpService;
import com.vidgrab.service.YtDlpService.VideoInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


import java.io.FilterInputStream;
import org.springframework.core.io.InputStreamResource;




@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VideoController {

    private static final Set<String> QUALITIES = Set.of("best", "1080", "720", "480", "audio");

    private final YtDlpService yt;
    private final PlatformRepository platforms;
    private final DownloadHistoryRepository history;
    private final FeedbackRepository feedbackRepo;

    /** /info me mili details ko download ke waqt history me save karne ke liye. */
    private final Map<String, VideoInfo> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, VideoInfo>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VideoInfo> e) {
                    return size() > 200;
                }
            });

    @PostMapping("/info")
    public ResponseEntity<?> info(@RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "").trim();
        Optional<Platform> platform = detect(url);
        if (platform.isEmpty()) return error(400, "Paste a valid YouTube, Instagram or Facebook link.");
        try {
            VideoInfo i = yt.fetchInfo(url);
            cache.put(url, i);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("platform", platform.get().getName());
            res.put("title", i.title());
            res.put("thumbnail", i.thumbnail());
            res.put("duration", i.duration());
            res.put("uploader", i.uploader());
            res.put("views", i.views());
            return ResponseEntity.ok(res);
        } catch (IllegalStateException e) {
            return error(422, e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam String url,
                                      @RequestParam(defaultValue = "best") String quality,
                                      HttpServletRequest req) {
        final String link = url.trim();
        final String q = QUALITIES.contains(quality) ? quality : "best";
        Optional<Platform> platform = detect(link);
        if (platform.isEmpty()) return error(400, "Paste a valid YouTube, Instagram or Facebook link.");

        DownloadHistory h = new DownloadHistory();
        h.setPlatform(platform.get());
        h.setVideoUrl(cut(link, 1000));
        h.setQuality(q);
        h.setClientIp(req.getRemoteAddr());
        VideoInfo info = cache.get(link);
        if (info != null) {
            h.setTitle(cut(info.title(), 500));
            h.setThumbnail(cut(info.thumbnail(), 1000));
            h.setUploader(cut(info.uploader(), 255));
            h.setDurationSeconds(info.duration());
        }

        try {
            Path file = yt.download(link, q);
            String name = file.getFileName().toString();
            long size = Files.size(file);
            h.setFileName(cut(name, 500));
            if (h.getTitle() == null) h.setTitle(cut(name.replaceFirst("\\.[^.]+$", ""), 500));
            h.setStatus("SUCCESS");
            history.save(h);

            InputStream in = new FilterInputStream(Files.newInputStream(file)) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        YtDlpService.deleteDir(file.getParent());
                    }
                }
            };
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                    .contentLength(size)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(in));

        } catch (IllegalStateException e) {
            return fail(h, e.getMessage());
        } catch (IOException e) {
            return fail(h, "Could not prepare the file.");
        }
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(@RequestParam(defaultValue = "8") int limit, HttpServletRequest req) {
        int max = Math.min(Math.max(limit, 1), 20);
        return history.findTop20ByClientIpOrderByCreatedAtDesc(req.getRemoteAddr()).stream()
                .limit(max)
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", h.getId());
                    m.put("platform", h.getPlatform().getName());
                    m.put("url", h.getVideoUrl());
                    m.put("title", h.getTitle());
                    m.put("thumbnail", h.getThumbnail());
                    m.put("quality", h.getQuality());
                    m.put("status", h.getStatus());
                    m.put("createdAt", h.getCreatedAt().toString());
                    return m;
                }).toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> per = new LinkedHashMap<>();
        for (Object[] r : history.countByPlatform()) per.put((String) r[0], (Long) r[1]);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", history.countByStatus("SUCCESS"));
        res.put("platforms", platforms.findByActiveTrue().size());
        res.put("byPlatform", per);
        return res;
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody Map<String, String> b) {
        String name = b.getOrDefault("name", "").trim();
        String email = b.getOrDefault("email", "").trim();
        String message = b.getOrDefault("message", "").trim();
        if (name.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || message.length() < 5) {
            return error(400, "Enter your name, a valid email, and a short message.");
        }
        Feedback f = new Feedback();
        f.setName(cut(name, 100));
        f.setEmail(cut(email, 150));
        f.setMessage(cut(message, 2000));
        feedbackRepo.save(f);
        return ResponseEntity.ok(Map.of("message", "Thanks for your feedback"));
    }

    private Optional<Platform> detect(String url) {
        try {
            URI u = new URI(url);
            String scheme = u.getScheme();
            String host = u.getHost();
            if (host == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                return Optional.empty();
            }
            String h = host.toLowerCase();
            return platforms.findByActiveTrue().stream()
                    .filter(p -> Arrays.stream(p.getDomains().split(","))
                            .map(String::trim)
                            .anyMatch(d -> h.equals(d) || h.endsWith("." + d)))
                    .findFirst();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private ResponseEntity<?> fail(DownloadHistory h, String msg) {
        h.setStatus("FAILED");
        h.setErrorMessage(cut(msg, 500));
        history.save(h);
        return error(422, msg);
    }

    private ResponseEntity<Map<String, String>> error(int code, String msg) {
        return ResponseEntity.status(code).body(Map.of("error", msg));
    }

    private String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
