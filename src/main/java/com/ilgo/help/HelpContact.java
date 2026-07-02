package com.ilgo.help;

/** 도움받을 곳 항목(§11). category: 사기|임금|외국인|건강보험|긴급. */
public record HelpContact(
        String id,
        String title,
        String org,
        String phone,
        String category,
        boolean emergency
) {
}
