package com.vidgrab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** yt-dlp ko process ki tarah chalakar video info aur file nikalta hai. */
@Service
public class YtDlpService {

    public record VideoInfo(String title, String thumbnail, Long duration, String uploader, Long views) {}

    private record Result(int code, String out, String err) {}

    @Value("${app.ytdlp.path:yt-dlp}")
    private String ytdlp;

    @Value("${app.ffmpeg.enabled:false}")
    private boolean ffmpeg;

    @Value("${app.timeout.seconds:180}")
    private long timeout;

    private final ObjectMapper mapper = new ObjectMapper();

    public VideoInfo fetchInfo(String url) {
        Result r = run(List.of(ytdlp, "-J", "--no-playlist", "--no-warnings", "--", url));
        if (r.code() != 0) throw new IllegalStateException(cleanError(r.err()));
        try {
            JsonNode n = mapper.readTree(r.out());
            return new VideoInfo(
                    text(n, "title", "Untitled video"),
                    text(n, "thumbnail", null),
                    n.hasNonNull("duration") ? n.get("duration").asLong() : null,
                    text(n, "uploader", text(n, "channel", null)),
                    n.hasNonNull("view_count") ? n.get("view_count").asLong() : null);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read video details.");
        }
    }

    /** Video ek temp folder me download karta hai. Caller ko folder delete karna hai. */
    public Path download(String url, String quality) {
        Path dir;
        try {
            dir = Files.createTempDirectory("vidgrab-");
        } catch (IOException e) {
            throw new IllegalStateException("Server storage error.");
        }
        List<String> cmd = new ArrayList<>(List.of(ytdlp, "--no-playlist", "--no-warnings", "--no-progress",
                "-f", formatFor(quality), "-o", dir.resolve("%(title).80s.%(ext)s").toString()));
        if (ffmpeg && !"audio".equals(quality)) cmd.addAll(List.of("--merge-output-format", "mp4"));
        cmd.add("--");
        cmd.add(url);

        try {
            Result r = run(cmd);
            if (r.code() != 0) throw new IllegalStateException(cleanError(r.err()));
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(p -> !p.toString().endsWith(".part")).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Video file could not be created."));
            }
        } catch (IOException e) {
            deleteDir(dir);
            throw new IllegalStateException("Could not prepare the file.");
        } catch (RuntimeException e) {
            deleteDir(dir);
            throw e;
        }
    }

    public static void deleteDir(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }

    private String formatFor(String q) {
        if (q == null) q = "best";
        if ("audio".equals(q)) return "ba[ext=m4a]/ba";
        if (ffmpeg) {
            return switch (q) {
                case "1080" -> "bv*[height<=1080]+ba/b[height<=1080]/b";
                case "720" -> "bv*[height<=720]+ba/b[height<=720]/b";
                case "480" -> "bv*[height<=480]+ba/b[height<=480]/b";
                default -> "bv*+ba/b";
            };
        }
        return switch (q) {
            case "1080" -> "b[height<=1080][ext=mp4]/b[height<=1080]/b";
            case "720" -> "b[height<=720][ext=mp4]/b[height<=720]/b";
            case "480" -> "b[height<=480][ext=mp4]/b[height<=480]/b";
            default -> "b[ext=mp4]/b";
        };
    }

    private Result run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(p.getInputStream()));
            CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> read(p.getErrorStream()));
            if (!p.waitFor(timeout, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("Request timed out. Try again.");
            }
            return new Result(p.exitValue(), out.get(5, TimeUnit.SECONDS), err.get(5, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new IllegalStateException("yt-dlp is not installed or not in PATH.");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Something went wrong. Try again.");
        }
    }

    private String read(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String text(JsonNode n, String key, String fallback) {
        return n.hasNonNull(key) ? n.get(key).asText() : fallback;
    }


    private String cleanError(String err) {
    System.err.println("yt-dlp error: " + err);
    String msg = "Could not fetch this video.";
    for (String line : err.split("\\R")) {
        if (line.startsWith("ERROR:")) msg = line.substring(6).trim();
    }
    return msg.length() > 250 ? msg.substring(0, 250) : msg;
}

    
    String low = msg.toLowerCase();
    if (low.contains("not a bot")) return "YouTube is asking for verification (bot check).";
    if (low.contains("private video")) return "This video is private.";
    if (low.contains("confirm your age")) return "This video is age-restricted.";
    return msg.length() > 200 ? msg.substring(0, 200) : msg;
}
