package com.ilgo.help;

import java.util.List;

/** 도움받을 곳 목록 응답(§11). JSON: { items: [ ... ] } */
public record HelpContactsResponse(List<HelpContact> items) {
}
