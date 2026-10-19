package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.Video;
import com.grove.Domain.VideoStatus;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

	List<Video> findAllByOrderByCreatedAtDesc();

	List<Video> findByStatusOrderByCreatedAtDesc(VideoStatus status);
}
