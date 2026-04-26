package org.hansung.zigma.domain.promise.repository;

import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromiseMemberRepository extends JpaRepository<PromiseMember, Long> {
}
