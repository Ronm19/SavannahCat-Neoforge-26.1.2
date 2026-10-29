package net.ronm19.savannahcat.util;

import java.util.Locale;

/** Exact ASCII name match: no trimming, prefixes, suffixes, or locale-dependent conversion. */
public final class SusanNameHelper {
    private SusanNameHelper() {}

    public static boolean isSusan(String name) {
        return name != null && "susan".equals(name.toLowerCase(Locale.ROOT));
    }
}
