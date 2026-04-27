package org.hansung.zigma.domain.promise.repository;

import org.hansung.zigma.domain.promise.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
}
