package org.hansung.zigma.domain.promise.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hansung.zigma.domain.promise.web.dto.CandidateCreateReq;
import org.hansung.zigma.domain.user.entity.User;
import org.hansung.zigma.global.entity.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "promise_candidates")
public class Candidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;    // 장소명

    @Column(nullable = false)
    private String address; // 주소

    @Column(nullable = false)
    private Double latitude; // 위도

    @Column(nullable = false)
    private Double longitude; // 경도

    private String category; // 카테고리 -> 일단 뭐 정해진게 없어서 String

    @Builder.Default
    @Column(nullable = false)
    private Boolean isConfirmed = false; // 확정 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promise_id", nullable = false)
    private Promise promise;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CandidateVote> candidateVotes = new ArrayList<>();

    // ------------------------------ 메서드 ------------------------------
    public static Candidate createCandidate(CandidateCreateReq req, User user, Promise promise) {
        return Candidate.builder()
                .name(req.getName())
                .address(req.getAddress())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .category(req.getCategory())
                .isConfirmed(false)
                .user(user)
                .promise(promise)
                .build();
    }

    public void setCandidateVote(CandidateVote candidateVote) {
        this.candidateVotes.add(candidateVote);
        candidateVote.setCandidate(this);
    }
}
