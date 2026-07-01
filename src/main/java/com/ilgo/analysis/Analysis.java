package com.ilgo.analysis;

import com.ilgo.user.OutputMode;
import com.ilgo.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analyses", indexes = {
        @Index(name = "ix_analyses_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "ix_analyses_image_hash", columnList = "image_hash")
})
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_analyses_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_mode", nullable = false, length = 16)
    private OutputMode outputMode;

    @Column(nullable = false, length = 8)
    private String lang;

    @Column(name = "doc_type", length = 16)
    private String docType;

    @Column(name = "image_ref", length = 512)
    private String imageRef;

    @Column(name = "image_hash", length = 64)
    private String imageHash;

    @Column(name = "summary_one_line", columnDefinition = "text")
    private String summaryOneLine;

    @Column(name = "original_text", columnDefinition = "text")
    private String originalText;

    @Column(name = "explained_text", columnDefinition = "text")
    private String explainedText;

    @Column(columnDefinition = "text")
    private String consequence;

    @Column(name = "card_what", length = 255)
    private String cardWhat;

    @Column(name = "card_when", length = 255)
    private String cardWhen;

    @Column(name = "card_where", length = 255)
    private String cardWhere;

    @Column(name = "card_amount", length = 255)
    private String cardAmount;

    @Column(name = "card_deadline")
    private LocalDate cardDeadline;

    @Column(length = 48)
    private String model;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "is_saved", nullable = false)
    private boolean isSaved = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Risk> risks = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    private List<Action> actions = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReplyDraft> replyDrafts = new ArrayList<>();

    public Analysis() {
    }

    /** 최고 위험 레벨(top_risk_level). risks 없으면 null. */
    public RiskLevel topRiskLevel() {
        RiskLevel top = null;
        for (Risk risk : risks) {
            if (risk.getLevel() == null) {
                continue;
            }
            if (top == null || risk.getLevel().getRank() > top.getRank()) {
                top = risk.getLevel();
            }
        }
        return top;
    }

    public void addRisk(Risk risk) {
        risk.setAnalysis(this);
        this.risks.add(risk);
    }

    public void addAction(Action action) {
        action.setAnalysis(this);
        this.actions.add(action);
    }

    public void addReplyDraft(ReplyDraft replyDraft) {
        replyDraft.setAnalysis(this);
        this.replyDrafts.add(replyDraft);
    }

    public void clearReplyDrafts() {
        this.replyDrafts.clear();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public void setOutputMode(OutputMode outputMode) {
        this.outputMode = outputMode;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getImageRef() {
        return imageRef;
    }

    public void setImageRef(String imageRef) {
        this.imageRef = imageRef;
    }

    public String getImageHash() {
        return imageHash;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public String getSummaryOneLine() {
        return summaryOneLine;
    }

    public void setSummaryOneLine(String summaryOneLine) {
        this.summaryOneLine = summaryOneLine;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getExplainedText() {
        return explainedText;
    }

    public void setExplainedText(String explainedText) {
        this.explainedText = explainedText;
    }

    public String getConsequence() {
        return consequence;
    }

    public void setConsequence(String consequence) {
        this.consequence = consequence;
    }

    public String getCardWhat() {
        return cardWhat;
    }

    public void setCardWhat(String cardWhat) {
        this.cardWhat = cardWhat;
    }

    public String getCardWhen() {
        return cardWhen;
    }

    public void setCardWhen(String cardWhen) {
        this.cardWhen = cardWhen;
    }

    public String getCardWhere() {
        return cardWhere;
    }

    public void setCardWhere(String cardWhere) {
        this.cardWhere = cardWhere;
    }

    public String getCardAmount() {
        return cardAmount;
    }

    public void setCardAmount(String cardAmount) {
        this.cardAmount = cardAmount;
    }

    public LocalDate getCardDeadline() {
        return cardDeadline;
    }

    public void setCardDeadline(LocalDate cardDeadline) {
        this.cardDeadline = cardDeadline;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isSaved() {
        return isSaved;
    }

    public void setSaved(boolean saved) {
        isSaved = saved;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Risk> getRisks() {
        return risks;
    }

    public List<Action> getActions() {
        return actions;
    }

    public List<ReplyDraft> getReplyDrafts() {
        return replyDrafts;
    }
}
