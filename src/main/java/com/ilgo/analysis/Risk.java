package com.ilgo.analysis;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "risks", indexes = @Index(name = "ix_risks_analysis", columnList = "analysis_id"))
public class Risk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_risks_analysis"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Analysis analysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskLevel level;

    /** 위험 유형(사기|임금|계약|기한|과태료|기타). API 값을 그대로 저장. */
    @Column(length = 16)
    private String type;

    @Column(columnDefinition = "text")
    private String message;

    /** 신호등 상세 설명(위험 상세 화면). 없으면 null. */
    @Column(columnDefinition = "text")
    private String detail;

    /** 단계별 대처 방법(위험 상세 화면). 순서 보존. 없으면 빈 리스트. */
    @ElementCollection
    @CollectionTable(name = "risk_steps",
            joinColumns = @JoinColumn(name = "risk_id", foreignKey = @ForeignKey(name = "fk_risk_steps_risk")))
    @OrderColumn(name = "step_order")
    @Column(name = "step", columnDefinition = "text")
    private List<String> steps = new ArrayList<>();

    public Risk() {
    }

    public Risk(RiskLevel level, String type, String message) {
        this(level, type, message, null, null);
    }

    public Risk(RiskLevel level, String type, String message, String detail, List<String> steps) {
        this.level = level;
        this.type = type;
        this.message = message;
        this.detail = detail;
        this.steps = (steps != null) ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public RiskLevel getLevel() {
        return level;
    }

    public void setLevel(RiskLevel level) {
        this.level = level;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = (steps != null) ? new ArrayList<>(steps) : new ArrayList<>();
    }
}
