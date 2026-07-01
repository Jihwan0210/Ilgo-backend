package com.ilgo.help;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 도움받을 곳 목록(§11). 현재는 큐레이션된 정적 목록을 제공한다.
 * lang/docType/region 은 향후 맞춤 필터용으로 받되, 지금은 전체 목록을 반환한다.
 */
@Service
public class HelpContactService {

    private static final List<HelpContact> CONTACTS = List.of(
            new HelpContact("hlp_1", "외국인 노동자 상담", "고용노동부", "1350", "labor", false),
            new HelpContact("hlp_2", "외국인종합안내센터", "법무부", "1345", "general", false),
            new HelpContact("hlp_3", "건강보험 문의", "국민건강보험공단", "1577-1000", "health", false),
            new HelpContact("hlp_4", "긴급 · 범죄 신고", "경찰", "112", "emergency", true));

    public HelpContactsResponse list(String lang, String docType, String region) {
        return new HelpContactsResponse(CONTACTS);
    }
}
