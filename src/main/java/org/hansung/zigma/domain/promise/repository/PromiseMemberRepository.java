package org.hansung.zigma.domain.promise.repository;

import org.hansung.zigma.domain.promise.entity.PromiseMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromiseMemberRepository extends JpaRepository<PromiseMember, Long> {

    Optional<PromiseMember> findByUserIdAndPromiseId(Long userId, Long promiseId);
}
