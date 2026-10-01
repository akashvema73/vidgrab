-- VidGrab database (MySQL 8)
CREATE DATABASE IF NOT EXISTS vidgrab CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vidgrab;

CREATE TABLE IF NOT EXISTS platforms (
  id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name      VARCHAR(50)  NOT NULL UNIQUE,
  domains   VARCHAR(255) NOT NULL COMMENT 'comma separated domains',
  active    TINYINT(1)   NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS download_history (
  id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  platform_id      BIGINT        NOT NULL,
  video_url        VARCHAR(1000) NOT NULL,
  title            VARCHAR(500),
  thumbnail        VARCHAR(1000),
  uploader         VARCHAR(255),
  duration_seconds BIGINT,
  quality          VARCHAR(20)   NOT NULL,
  file_name        VARCHAR(500),
  status           VARCHAR(10)   NOT NULL DEFAULT 'SUCCESS',
  error_message    VARCHAR(500),
  client_ip        VARCHAR(45),
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_history_platform FOREIGN KEY (platform_id) REFERENCES platforms(id),
  INDEX idx_history_ip_date (client_ip, created_at),
  INDEX idx_history_status (status)
);

CREATE TABLE IF NOT EXISTS feedback (
  id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(100) NOT NULL,
  email      VARCHAR(150) NOT NULL,
  message    VARCHAR(2000) NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT IGNORE INTO platforms (name, domains) VALUES
 ('YouTube',   'youtube.com,youtu.be,music.youtube.com'),
 ('Instagram', 'instagram.com'),
 ('Facebook',  'facebook.com,fb.watch,fb.com');
