package com.ilgo.help;

import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "도움받을 곳 목록", description = "언어·문서타입·지역 맞춤(현재는 정적 목록). 긴급 항목 포함.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public HelpContactsResponse list(
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "doc_type", required = false) String docType,
            @RequestParam(name = "region", required = false) String region) {
        return helpContactService.list(lang, docType, region);
    }
}
