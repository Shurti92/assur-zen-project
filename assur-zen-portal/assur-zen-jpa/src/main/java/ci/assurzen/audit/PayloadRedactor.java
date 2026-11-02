package ci.assurzen.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PayloadRedactor {

    private static final int MAX_CAPTURE_BYTES = 32 * 1024;
    private static final String MASK = "***";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> SENSITIVE_SUBSTRINGS = List.of("password", "otp", "token", "secret");
    private static final List<String> BINARY_CONTENT_TYPE_PREFIXES = List.of(
            "image/", "application/octet-stream", "application/pdf", "multipart/",
            "application/zip", "application/vnd.openxmlformats", "application/msword", "application/vnd.ms-excel"
    );

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)\"([a-zA-Z]*(?:password|otp|token|secret)[a-zA-Z]*)\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern OTP_CODE_FIELD_PATTERN = Pattern.compile("(?i)\"code\"\\s*:\\s*\"[^\"]*\"");

    private static final List<String> CONTRAT_KEY_CANDIDATES = List.of("numerocontrat", "numero_contrat", "policynumber");

    private PayloadRedactor() {
    }

    public static String redactJson(byte[] rawBytes, String contentType, boolean otpRoute) {
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }

        if (isBinaryContentType(contentType)) {
            return "[binary content-type=" + contentType + " size=" + rawBytes.length + " bytes, not captured]";
        }

        boolean truncated = rawBytes.length > MAX_CAPTURE_BYTES;
        byte[] captured = truncated ? Arrays.copyOf(rawBytes, MAX_CAPTURE_BYTES) : rawBytes;
        String text = new String(captured, StandardCharsets.UTF_8);

        String result;
        if (looksLikeJson(contentType, text)) {
            try {
                JsonNode root = MAPPER.readTree(text);
                redactNode(root, otpRoute);
                result = MAPPER.writeValueAsString(root);
            } catch (Exception e) {
                result = fallbackRedact(text, otpRoute);
            }
        } else {
            result = fallbackRedact(text, otpRoute);
        }

        if (truncated) {
            result = result + "...[truncated, " + rawBytes.length + " bytes total]";
        }
        return result;
    }

    private static void redactNode(JsonNode node, boolean otpRoute) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveKey(key, otpRoute)) {
                    objectNode.put(key, MASK);
                } else if (value.isObject() || value.isArray()) {
                    redactNode(value, otpRoute);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode element : arrayNode) {
                redactNode(element, otpRoute);
            }
        }
    }

    private static boolean isSensitiveKey(String key, boolean otpRoute) {
        String lower = key.toLowerCase();
        if (otpRoute && lower.equals("code")) {
            return true;
        }
        for (String substring : SENSITIVE_SUBSTRINGS) {
            if (lower.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    private static String fallbackRedact(String text, boolean otpRoute) {
        Matcher matcher = SENSITIVE_FIELD_PATTERN.matcher(text);
        String redacted = matcher.replaceAll(mr -> "\"" + mr.group(1) + "\":\"" + MASK + "\"");
        if (otpRoute) {
            Matcher otpMatcher = OTP_CODE_FIELD_PATTERN.matcher(redacted);
            redacted = otpMatcher.replaceAll("\"code\":\"" + MASK + "\"");
        }
        return redacted;
    }

    private static boolean looksLikeJson(String contentType, String text) {
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            if (lower.contains("application/json") || lower.contains("+json")) {
                return true;
            }
        }
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static boolean isBinaryContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return BINARY_CONTENT_TYPE_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    /** Reutilise la meme liste de mots-cles sensibles que redactJson, pour masquer un champ isole (ex. dans un diff avant/apres). */
    public static boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return SENSITIVE_SUBSTRINGS.stream().anyMatch(lower::contains);
    }

    public static boolean isOtpRoute(String path) {
        if (path == null) {
            return false;
        }
        return path.contains("/otp/verify") || path.contains("/login/otp-verify") || path.contains("/auth/reset-password");
    }

    public static String extractNumeroContrat(byte[] rawBodyBytes, String contentType, Map<String, String[]> queryParams) {
        if (rawBodyBytes != null && rawBodyBytes.length > 0 && looksLikeJson(contentType, new String(rawBodyBytes, StandardCharsets.UTF_8))) {
            try {
                JsonNode root = MAPPER.readTree(rawBodyBytes);
                String fromBody = findContractKey(root);
                if (fromBody != null) {
                    return fromBody;
                }
            } catch (Exception ignored) {
                // not parseable JSON — fall through to query params
            }
        }
        if (queryParams != null) {
            for (Map.Entry<String, String[]> entry : queryParams.entrySet()) {
                if (CONTRAT_KEY_CANDIDATES.contains(entry.getKey().toLowerCase())
                        && entry.getValue() != null && entry.getValue().length > 0) {
                    return entry.getValue()[0];
                }
            }
        }
        return null;
    }

    private static String findContractKey(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (CONTRAT_KEY_CANDIDATES.contains(entry.getKey().toLowerCase()) && entry.getValue().isTextual()) {
                return entry.getValue().asText();
            }
        }
        return null;
    }
}
