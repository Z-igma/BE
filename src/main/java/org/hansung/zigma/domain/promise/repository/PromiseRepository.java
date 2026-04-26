package org.hansung.zigma.domain.promise.repository;

import org.hansung.zigma.domain.promise.entity.Promise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromiseRepository extends JpaRepository<Promise, Long> {
}
