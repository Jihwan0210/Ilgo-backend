package com.ilgo.help;

/** 도움받을 곳 항목(§11). category: labor|general|health|emergency. */
public record HelpContact(
        String id,
        String title,
        String org,
        String phone,
        String category,
        boolean emergency
) {
}
