package com.vidgrab.repository;

import com.vidgrab.entity.DownloadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {
    List<DownloadHistory> findTop20ByClientIpOrderByCreatedAtDesc(String clientIp);

    long countByStatus(String status);

    @Query("select d.platform.name, count(d) from DownloadHistory d where d.status = 'SUCCESS' group by d.platform.name")
    List<Object[]> countByPlatform();
}
