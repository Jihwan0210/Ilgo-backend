package com.ilgo.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "reply_drafts", indexes = @Index(name = "ix_reply_drafts_analysis", columnList = "analysis_id"))
public class ReplyDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reply_drafts_analysis"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Analysis analysis;

    /** 정중한 한국어 답장. */
    @Column(columnDefinition = "text")
    private String korean;

    /** 한국어 답장에 대한 사용자 언어 해설/번역. */
    @Column(name = "note_in_lang", columnDefinition = "text")
    private String noteInLang;

    public ReplyDraft() {
    }

    public ReplyDraft(String korean, String noteInLang) {
        this.korean = korean;
        this.noteInLang = noteInLang;
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

    public String getKorean() {
        return korean;
    }

    public void setKorean(String korean) {
        this.korean = korean;
    }

    public String getNoteInLang() {
        return noteInLang;
    }

    public void setNoteInLang(String noteInLang) {
        this.noteInLang = noteInLang;
    }
}
