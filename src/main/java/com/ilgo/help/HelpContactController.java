package com.ilgo.help;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/help-contacts")
@Tag(name = "HelpContacts", description = "도움받을 곳 목록")
public class HelpContactController {

    private final HelpContactService helpContactService;

    public HelpContactController(HelpContactService helpContactService) {
        this.helpContactService = helpContactService;
    }

    @Operation(summary = "도움받을 곳 목록",
            description = "언어·문서타입·지역·위험유형 맞춤(현재는 정적 목록). risk_type 이 있으면 해당 위험에 맞는 연락처를 앞으로 정렬한다. 알 수 없는 risk_type 도 200 + 기본 목록. 긴급 항목 포함.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public HelpContactsResponse list(
            @Parameter(description = "사용자 언어 (ko, en 등)")
            @RequestParam(name = "lang", required = false) String lang,
            @Parameter(description = "문서 타입 (메시지, 고지서, 계약 등)")
            @RequestParam(name = "doc_type", required = false) String docType,
            @RequestParam(name = "region", required = false) String region,
            @Parameter(description = "위험 유형 (사기, 스미싱, 임금, 계약, 과태료, 기한, 기타). 해당 위험의 추천 연락처를 첫 번째로 반환.")
            @RequestParam(name = "risk_type", required = false) String riskType) {
        return helpContactService.list(lang, docType, region, riskType);
    }
}
