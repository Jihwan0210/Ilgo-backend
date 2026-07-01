package com.ilgo.analysis;

import com.ilgo.user.OutputMode;
import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티 매핑 + 레포지토리(H2 슬라이스) 검증.
 * 스키마 생성, 자식 cascade, top_risk_level 계산, 보관함 커서 쿼리를 확인한다.
 */
@DataJpaTest
class AnalysisMappingTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    private Analysis newAnalysis(User user, AnalysisStatus status) {
        Analysis a = new Analysis();
        a.setUser(user);
        a.setStatus(status);
        a.setOutputMode(OutputMode.EASY_KOREAN);
        a.setLang("ko");
        a.setDocType("고지서");
        a.setSummaryOneLine("한 줄 요약");
        return a;
    }

    @Test
    void persistsAnalysisWithChildrenAndComputesTopRiskLevel() {
        User user = userRepository.save(new User("device-A"));

        Analysis a = newAnalysis(user, AnalysisStatus.DONE);
        a.addRisk(new Risk(RiskLevel.YELLOW, "기한", "노랑 위험"));
        a.addRisk(new Risk(RiskLevel.RED, "과태료", "빨강 위험"));
        a.addAction(new Action("먼저 이것부터", 1));
        a.addAction(new Action("그다음 이것", 2));
        a.addReplyDraft(new ReplyDraft("안녕하세요", "hello note"));
        analysisRepository.save(a);

        em.flush();
        em.clear();

        Analysis loaded = analysisRepository.findById(a.getId()).orElseThrow();
        assertThat(loaded.getRisks()).hasSize(2);
        assertThat(loaded.getActions()).hasSize(2);
        assertThat(loaded.getReplyDrafts()).hasSize(1);
        assertThat(loaded.getActions().get(0).getPriority()).isEqualTo(1); // priority ASC 정렬
        assertThat(loaded.topRiskLevel()).isEqualTo(RiskLevel.RED);        // RED > YELLOW
    }

    @Test
    void topRiskLevelIsNullWhenNoRisks() {
        User user = userRepository.save(new User("device-B"));
        Analysis a = analysisRepository.save(newAnalysis(user, AnalysisStatus.DONE));
        em.flush();
        em.clear();

        assertThat(analysisRepository.findById(a.getId()).orElseThrow().topRiskLevel()).isNull();
    }

    @Test
    void cascadeDeletesChildren() {
        User user = userRepository.save(new User("device-C"));
        Analysis a = newAnalysis(user, AnalysisStatus.DONE);
        a.addRisk(new Risk(RiskLevel.GREEN, "기타", "안전"));
        a.addAction(new Action("할 일", 1));
        analysisRepository.save(a);
        em.flush();

        Long risksBefore = em.getEntityManager()
                .createQuery("select count(r) from Risk r", Long.class).getSingleResult();
        assertThat(risksBefore).isEqualTo(1L);

        analysisRepository.delete(a);
        em.flush();

        Long risksAfter = em.getEntityManager()
                .createQuery("select count(r) from Risk r", Long.class).getSingleResult();
        Long actionsAfter = em.getEntityManager()
                .createQuery("select count(x) from Action x", Long.class).getSingleResult();
        assertThat(risksAfter).isZero();
        assertThat(actionsAfter).isZero();
    }

    @Test
    void firstPageQueryReturnsOnlyOwnersSavedItems() {
        User owner = userRepository.save(new User("device-owner"));
        User other = userRepository.save(new User("device-other"));

        analysisRepository.save(newAnalysis(owner, AnalysisStatus.DONE));
        analysisRepository.save(newAnalysis(owner, AnalysisStatus.DONE));
        Analysis hidden = newAnalysis(owner, AnalysisStatus.DONE);
        hidden.setSaved(false);
        analysisRepository.save(hidden);
        analysisRepository.save(newAnalysis(other, AnalysisStatus.DONE));
        em.flush();
        em.clear();

        List<Analysis> savedOnly = analysisRepository.findFirstPage(owner.getId(), true, PageRequest.of(0, 20));
        assertThat(savedOnly).hasSize(2);

        List<Analysis> all = analysisRepository.findFirstPage(owner.getId(), false, PageRequest.of(0, 20));
        assertThat(all).hasSize(3);
    }
}
