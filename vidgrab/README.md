# VidGrab - YouTube + Instagram + Facebook Video Downloader

Tech: Java 17, Spring Boot 3, Spring Data JPA, MySQL 8, HTML, CSS, JavaScript, yt-dlp

## Setup
1. Install JDK 17+, Maven, MySQL 8.
2. Install yt-dlp and make sure `yt-dlp --version` works in your terminal
   (`pip install yt-dlp` or download from github.com/yt-dlp/yt-dlp/releases).
3. Optional but recommended: install ffmpeg, then set `app.ffmpeg.enabled=true`
   in application.properties (needed for 1080p and Best quality on YouTube).
4. Database: run `database/vidgrab.sql` in MySQL Workbench
   (or skip it; the app creates the tables and seeds the 3 platforms itself).
5. Edit MySQL username and password in `src/main/resources/application.properties`.
6. Run: `mvn spring-boot:run` and open http://localhost:8080

## API
| Method | URL | Purpose |
|--------|-----|---------|
| POST | /api/info | Video title, thumbnail, duration for a link |
| GET | /api/download?url=&quality= | Download the file (best, 1080, 720, 480, audio) |
| GET | /api/history | Recent downloads of this client |
| GET | /api/stats | Total downloads and platform count |
| POST | /api/feedback | Save feedback |

## Database tables
- platforms: YouTube, Instagram, Facebook and their domains
- download_history: every download attempt (url, title, quality, status, ip, time)
- feedback: messages from the feedback form

## Notes
- Only public videos work. Private Instagram/Facebook videos need login cookies.
- If downloads suddenly fail, update yt-dlp: `pip install -U yt-dlp`.
- Use only for content you own or have permission to download.
