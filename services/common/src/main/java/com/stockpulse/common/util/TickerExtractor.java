package com.stockpulse.common.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class TickerExtractor {
    private TickerExtractor() {}

    private static final Pattern TICKER_PATTERN =
        Pattern.compile("\\$([A-Z]{1,5})|\\b([A-Z]{2,5})\\b");

    private static final Set<String> BLOCKLIST = Set.of(
        "I","A","AN","THE","IN","ON","AT","OR","AND","BUT","FOR","NOT",
        "BY","UP","TO","DO","GO","IF","MY","IT","IS","AM","ARE","WAS",
        "BE","AS","OF","SO","NO","CEO","CFO","IPO","ETF","EPS","PE",
        "DD","OP","US","UK","EU","USA","GDP","FED","SEC","AI","ML",
        "OK","LOL","IMO","YOLO","WSB","BTC","ETH","COVID","FDA","WHO"
    );

    public static Set<String> extract(String text) {
        var found = new HashSet<String>();
        var matcher = TICKER_PATTERN.matcher(text);
        while (matcher.find()) {
            var dollar = matcher.group(1);
            if (dollar != null) { found.add(dollar); continue; }
            var bare = matcher.group(2);
            if (bare != null && !BLOCKLIST.contains(bare)) found.add(bare);
        }
        return found;
    }
}
