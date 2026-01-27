package tech.icc.filesrv.common.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class StringUtils {
    public static String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
