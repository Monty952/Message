package com.example.message.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent helper for detecting and extracting OTPs and verification codes
 * from SMS messages while preventing false positives from dates, currency amounts,
 * account numbers, and transaction IDs.
 */
public class OtpHelper {

    // General OTP intent indicators (must be present in message)
    private static final Pattern OTP_INTENT_PATTERN = Pattern.compile(
            "(?i)\\b(?:otp|one[- ]time\\s*(?:pass(?:word|code)|pin)|verification\\s*code|verify|security\\s*code|login\\s*code|access\\s*code|auth(?:entication|orization)?\\s*code|passcode|validation\\s*code|secret\\s*code|confirmation\\s*code|2fa|two[- ]factor)\\b"
    );

    // Explicit label followed by code: e.g. "Your OTP is 123456", "verification code: 4892", "OTP - 9283"
    private static final Pattern EXPLICIT_OTP_PATTERN = Pattern.compile(
            "(?i)\\b(?:otp|one[- ]time\\s*(?:pass(?:word|code)|pin)|verification\\s*code|security\\s*code|login\\s*code|access\\s*code|auth(?:entication|orization)?\\s*code|passcode|confirmation\\s*code|validation\\s*code)\\s*(?:is|:|-|=|–|\\s)\\s*[:#]?\\s*([A-Za-z0-9]{4,8})\\b"
    );

    // Shorter label: e.g. "code is 123456", "pin: 1234"
    private static final Pattern CODE_OR_PIN_PATTERN = Pattern.compile(
            "(?i)\\b(?:code|pin)\\s*(?:is|:|-|=|–)\\s*[:#]?\\s*([A-Za-z0-9]{4,8})\\b"
    );

    // Leading OTP: e.g. "482910 is your WhatsApp code", "1829 is your verification code"
    private static final Pattern LEADING_OTP_PATTERN = Pattern.compile(
            "(?i)^([0-9]{4,8})\\s+(?:is\\s+your|is\\s+the)\\b"
    );

    // "Use <CODE> to verify/login/for"
    private static final Pattern USE_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(?:use|enter)\\s+([A-Za-z0-9]{4,8})\\s+(?:as\\s+your|to\\s+verify|to\\s+login|to\\s+sign\\s+in|for\\s+verification|for\\s+your|to\\s+complete)\\b"
    );

    // Service-prefixed code: e.g. "G-123456 is your Google verification code"
    private static final Pattern PREFIXED_CODE_PATTERN = Pattern.compile(
            "(?i)\\b[A-Za-z]-([0-9]{4,8})\\b"
    );

    // Words that should never be considered alphanumeric OTPs
    private static final Set<String> BANNED_WORDS = new HashSet<>(Arrays.asList(
            "CODE", "YOUR", "THIS", "VALID", "PLEASE", "SHARE", "ACCOUNT",
            "ENTER", "VERIFY", "LOGIN", "ACCESS", "ONLINE", "NUMBER", "MOBILE",
            "UPDATE", "DEVICE", "SERVICE", "BANK", "SECURE", "CARD", "EXPIRE",
            "MINUTES", "HOURS", "CALL", "HELP", "WITH", "FROM", "DATE", "TIME"
    ));

    /**
     * Determines whether the given SMS text represents an OTP or verification message.
     */
    public static boolean isOtpMessage(String message) {
        if (message == null || message.trim().isEmpty()) return false;
        return OTP_INTENT_PATTERN.matcher(message).find();
    }

    /**
     * Extracts an OTP or verification code from the message text.
     * Returns the extracted code, or null if no valid OTP is found.
     */
    public static String extractOtp(String message) {
        if (message == null || message.trim().isEmpty()) return null;

        // 1. Gate check: Message must contain an OTP intent indicator
        if (!isOtpMessage(message)) {
            return null;
        }

        // 2. High-confidence explicit patterns
        // Check service-prefixed code first (e.g. G-123456)
        Matcher prefixMatcher = PREFIXED_CODE_PATTERN.matcher(message);
        if (prefixMatcher.find()) {
            String candidate = prefixMatcher.group(1);
            if (isValidCandidate(candidate, message, prefixMatcher.start(1))) {
                return candidate;
            }
        }

        // Check leading code (e.g. "482910 is your code")
        Matcher leadingMatcher = LEADING_OTP_PATTERN.matcher(message.trim());
        if (leadingMatcher.find()) {
            String candidate = leadingMatcher.group(1);
            if (isValidCandidate(candidate, message, leadingMatcher.start(1))) {
                return candidate;
            }
        }

        // Check explicit labeled code (e.g. "Your OTP is 123456")
        Matcher explicitMatcher = EXPLICIT_OTP_PATTERN.matcher(message);
        if (explicitMatcher.find()) {
            String candidate = explicitMatcher.group(1);
            if (isValidCandidate(candidate, message, explicitMatcher.start(1))) {
                return candidate;
            }
        }

        // Check "Use <CODE> to verify" pattern
        Matcher useMatcher = USE_CODE_PATTERN.matcher(message);
        if (useMatcher.find()) {
            String candidate = useMatcher.group(1);
            if (isValidCandidate(candidate, message, useMatcher.start(1))) {
                return candidate;
            }
        }

        // Check "code is <CODE>" pattern
        Matcher codeMatcher = CODE_OR_PIN_PATTERN.matcher(message);
        if (codeMatcher.find()) {
            String candidate = codeMatcher.group(1);
            if (isValidCandidate(candidate, message, codeMatcher.start(1))) {
                return candidate;
            }
        }

        // 3. Fallback: Proximity candidate scanning
        return findClosestOtpCandidate(message);
    }

    /**
     * Validates that a candidate code is not a banned word, currency, account number,
     * or date.
     */
    private static boolean isValidCandidate(String candidate, String fullText, int startIndex) {
        if (candidate == null) return false;
        int len = candidate.length();
        if (len < 4 || len > 8) return false;

        String upper = candidate.toUpperCase(Locale.US);
        if (BANNED_WORDS.contains(upper)) return false;

        // An alphanumeric code must have at least one digit
        boolean hasDigit = false;
        for (int i = 0; i < len; i++) {
            if (Character.isDigit(candidate.charAt(i))) {
                hasDigit = true;
                break;
            }
        }
        if (!hasDigit) return false;

        // Check preceding context to reject amounts, account numbers, dates
        if (startIndex > 0) {
            int lookbackStart = Math.max(0, startIndex - 25);
            String preceding = fullText.substring(lookbackStart, startIndex).toLowerCase(Locale.US).trim();

            // Currency check: Rs, INR, $, etc.
            if (preceding.endsWith("rs.") || preceding.endsWith("rs") || preceding.endsWith("inr")
                    || preceding.endsWith("$") || preceding.endsWith("€") || preceding.endsWith("£")
                    || preceding.endsWith("₹") || preceding.endsWith("usd") || preceding.endsWith("eur")) {
                return false;
            }

            // Account / Card ending check
            if (preceding.contains("ending") || preceding.contains("a/c") || preceding.contains("acct")
                    || preceding.contains("account") || preceding.contains("card")) {
                return false;
            }

            // Transaction / Reference check
            if (preceding.contains("txn") || preceding.contains("transaction") || preceding.contains("ref")
                    || preceding.contains("reference") || preceding.contains("utr") || preceding.contains("order")) {
                return false;
            }
        }

        // Reject if candidate looks like a common calendar year (2020 - 2035) in date context
        if (len == 4 && candidate.matches("20[23][0-9]")) {
            if (fullText.contains("-") || fullText.contains("/") || fullText.contains(".")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Scans all 4-8 digit / alphanumeric tokens in the message and selects
     * the valid candidate closest to an OTP keyword.
     */
    private static String findClosestOtpCandidate(String message) {
        // Find position of primary OTP keyword
        Matcher intentMatcher = OTP_INTENT_PATTERN.matcher(message);
        int keywordPos = -1;
        if (intentMatcher.find()) {
            keywordPos = intentMatcher.start();
        }
        if (keywordPos == -1) return null;

        // Match all numeric tokens of length 4 to 8
        Pattern tokenPattern = Pattern.compile("\\b([A-Za-z0-9]{4,8})\\b");
        Matcher matcher = tokenPattern.matcher(message);

        String bestCandidate = null;
        int minDistance = Integer.MAX_VALUE;

        while (matcher.find()) {
            String token = matcher.group(1);
            int tokenPos = matcher.start();
            if (isValidCandidate(token, message, tokenPos)) {
                int dist = Math.abs(tokenPos - keywordPos);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestCandidate = token;
                }
            }
        }

        return bestCandidate;
    }
}
