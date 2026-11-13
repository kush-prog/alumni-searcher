package com.alumni.searcher.repository;

import com.alumni.searcher.entity.Alumni;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AlumniRepository extends JpaRepository<Alumni, Long> {

    List<Alumni> findByProfileUrlIn(Collection<String> profileUrls);

    List<Alumni> findAllByOrderByUpdatedAtDesc();
}
