package com.ilgo.help;

import com.ilgo.analysis.RiskTypes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 도움받을 곳 목록(§11 / BE-HC). 큐레이션된 정적 목록을 제공한다.
 * risk_type 이 있으면 해당 위험에 가장 적합한 연락처를 앞으로 정렬한다(BE-HC-02).
 * lang/docType/region 은 현재 필터에 쓰지 않고 전체 목록을 반환한다(폴백은 항상 성공).
 */
@Service
public class HelpContactService {

    /** 기본 노출 순서. risk_type 이 없거나 알 수 없으면 이 순서 그대로 반환한다. */
    private static final List<HelpContact> CONTACTS = List.of(
            new HelpContact("kisa_118", "스미싱·해킹 상담", "KISA/보호나라", "118", "사기", false),
            new HelpContact("moel_1350", "임금·노동 상담", "고용노동부", "1350", "임금", false),
            new HelpContact("immigration_1345", "외국인종합안내센터", "법무부", "1345", "외국인", false),
            new HelpContact("nhis_1577_1000", "건강보험 문의", "국민건강보험공단", "1577-1000", "건강보험", false),
            new HelpContact("police_112", "긴급 · 범죄 신고", "경찰", "112", "긴급", true));

    private static final Map<String, HelpContact> BY_ID = index(CONTACTS);

    /** 위험 계열별 우선 노출 연락처 id(BE-HC-02). 나머지는 기본 순서로 뒤에 붙는다. */
    private static final Map<RiskTypes.Family, List<String>> PRIORITY = Map.of(
            RiskTypes.Family.FRAUD, List.of("kisa_118", "police_112"),
            RiskTypes.Family.LABOR, List.of("moel_1350", "immigration_1345"),
            RiskTypes.Family.CONTRACT, List.of("moel_1350", "immigration_1345"),
            RiskTypes.Family.HEALTH, List.of("nhis_1577_1000", "kisa_118"));

    public HelpContactsResponse list(String lang, String docType, String region, String riskType) {
        List<HelpContact> items = sortForRisk(riskType);
        return new HelpContactsResponse(items);
    }

    /** riskType 계열의 우선 연락처를 앞으로, 나머지는 기본 순서로 뒤에 붙여 반환한다. */
    private List<HelpContact> sortForRisk(String riskType) {
        if (riskType == null || riskType.isBlank()) {
            return CONTACTS;
        }
        List<String> priorityIds = PRIORITY.get(RiskTypes.family(riskType));
        if (priorityIds == null || priorityIds.isEmpty()) {
            return CONTACTS; // FINE/OTHER/미지 → 기본 목록(폴백)
        }
        List<HelpContact> ordered = new ArrayList<>(CONTACTS.size());
        for (String id : priorityIds) {
            HelpContact c = BY_ID.get(id);
            if (c != null && c.phone() != null && !c.phone().isBlank()) {
                ordered.add(c);
            }
        }
        for (HelpContact c : CONTACTS) {
            if (!ordered.contains(c) && c.phone() != null && !c.phone().isBlank()) {
                ordered.add(c);
            }
        }
        return ordered;
    }

    private static Map<String, HelpContact> index(List<HelpContact> contacts) {
        Map<String, HelpContact> map = new LinkedHashMap<>();
        for (HelpContact c : contacts) {
            map.put(c.id(), c);
        }
        return map;
    }
}
