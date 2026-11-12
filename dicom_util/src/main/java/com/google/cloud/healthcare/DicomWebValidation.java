package com.google.cloud.healthcare;

import com.google.common.base.Strings;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DicomWebValidation {
    private static Logger log = LoggerFactory.getLogger(DicomWebValidation.class);

    private static String DICOMWEB_PATH = "https:\\/\\/healthcare.googleapis.com\\/.*?\\/projects\\/.*?\\/locations\\/.*?\\/datasets\\/.*?\\/dicomStores\\/.*?\\/dicomWeb";
    private static String HEALTHCARE_API_ROOT = "https://healthcare.googleapis.com";

    private static final String SAFE_SEGMENT = "[^/\\\\?#]+";
    private static final Pattern WADO_RS_INSTANCE_PATH_PATTERN =
        Pattern.compile(
            String.format(
                "^(?:projects\\/%1$s\\/locations\\/%1$s\\/datasets\\/%1$s\\/dicomStores\\/%1$s\\/dicomWeb\\/)?"
                    + "studies\\/%1$s\\/series\\/%1$s\\/instances\\/%1$s$",
                SAFE_SEGMENT));

    public static ValidationPattern DICOMWEB_ROOT_VALIDATION =
            new ValidationPattern(Pattern.compile(DICOMWEB_PATH), "Google Healthcare Api dicomWeb root path");

    public static String validatePath(String path, ValidationPattern validation){
        path = StringUtil.trim(path);
        if(path.startsWith(HEALTHCARE_API_ROOT)) {
            if (!validation.pattern.matcher(path).matches()) {
                throw new IllegalArgumentException("Path: " + path + " is not a valid " + validation.name);
            }
        }
        return path;
    }

    /**
     * Validates a relative DICOMweb request path to prevent path traversal, encoded slash/dot
     * bypasses, and query/fragment injection.
     */
    public static String validateRelativeRequestPath(String path, boolean allowQuery) {
        if (path == null) {
            throw new IllegalArgumentException("DICOMweb request path must not be null");
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c <= 0x1F || c == 0x7F || Character.isWhitespace(c)) {
                throw new IllegalArgumentException(
                    "Invalid control or whitespace character in DICOMweb path: " + path);
            }
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Invalid character in DICOMweb path: " + path);
        }
        if (!allowQuery && path.indexOf('?') >= 0) {
            throw new IllegalArgumentException("Unexpected query string in DICOMweb path: " + path);
        }

        String trimmed = StringUtil.trim(path);
        if (Strings.isNullOrEmpty(trimmed)) {
            throw new IllegalArgumentException("DICOMweb request path must not be empty");
        }

        int queryIdx = trimmed.indexOf('?');
        String pathPart = queryIdx >= 0 ? trimmed.substring(0, queryIdx) : trimmed;
        if (Strings.isNullOrEmpty(pathPart)
            || pathPart.startsWith("/")
            || pathPart.endsWith("/")) {
            throw new IllegalArgumentException("Invalid DICOMweb path: " + path);
        }

        String lowerPathPart = pathPart.toLowerCase(Locale.ROOT);
        if (lowerPathPart.contains("%2f")
            || lowerPathPart.contains("%5c")
            || lowerPathPart.contains("%00")) {
            throw new IllegalArgumentException("Encoded path separator in DICOMweb path: " + path);
        }

        String[] segments = pathPart.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Invalid path segment in DICOMweb path: " + path);
            }
            String decodedSegment = urlDecodeSegment(segment, path);
            if (decodedSegment.isEmpty()
                || ".".equals(decodedSegment)
                || "..".equals(decodedSegment)
                || decodedSegment.indexOf('/') >= 0
                || decodedSegment.indexOf('\\') >= 0) {
                throw new IllegalArgumentException(
                    "Invalid decoded path segment in DICOMweb path: " + path);
            }
        }
        return trimmed;
    }

    /**
     * Validates a WADO-RS instance path received from a Cloud Pub/Sub export notification.
     * Supports both store-relative paths ({@code studies/<study>/series/<series>/instances/<instance>})
     * and Cloud Healthcare API resource-relative paths
     * ({@code projects/<p>/locations/<l>/datasets/<d>/dicomStores/<s>/dicomWeb/studies/<study>/series/<series>/instances/<instance>}).
     */
    public static String validateWadoRsInstancePath(String wadoUri) {
        String trimmed = validateRelativeRequestPath(wadoUri, false /* allowQuery */);
        if (!WADO_RS_INSTANCE_PATH_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                "Invalid WADO-RS instance path in Pub/Sub message: " + wadoUri);
        }
        return trimmed;
    }

    private static String urlDecodeSegment(String segment, String fullPath) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Malformed percent-encoding in DICOMweb path: " + fullPath, e);
        }
    }

    private static class ValidationPattern{
        private Pattern pattern;
        private String name;

        private ValidationPattern(Pattern pattern, String name){
            this.pattern = pattern;
            this.name = name;
        }
    }
}
