package com.saferide.complaint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Page<Complaint> findByFiledByIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Complaint> findByStatusOrderByCreatedAtAsc(ComplaintStatus status, Pageable pageable);
}
