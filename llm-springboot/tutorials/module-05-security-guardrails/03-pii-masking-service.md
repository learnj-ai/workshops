# PII Masking Service

## Overview

The `PIIMaskingService` protects personally identifiable information (PII) by detecting and redacting sensitive data in both user inputs and AI-generated outputs. This prevents data leakage, ensures compliance with privacy regulations (GDPR, CCPA, HIPAA), and protects users from accidental exposure of sensitive information.

This component supports detection and masking of emails, phone numbers, Social Security Numbers (SSNs), and credit card numbers using regex-based pattern matching.

## What is PII?

**Personally Identifiable Information (PII)** is any data that could identify a specific individual:

- Email addresses: `john.doe@example.com`
- Phone numbers: `555-123-4567`, `(555) 123-4567`
- Social Security Numbers: `123-45-6789`
- Credit card numbers: `1234-5678-9012-3456`
- Other: Addresses, passport numbers, driver's licenses, biometric data

## Why Mask PII?

### Compliance Requirements

**GDPR (Europe)**: Right to erasure, data minimization, and privacy by design
**CCPA (California)**: Consumer data protection and disclosure requirements
**HIPAA (Healthcare)**: Protected Health Information (PHI) safeguards
**PCI DSS (Payments)**: Credit card data protection standards

### Security Benefits

1. **Prevents data leakage**: LLMs might memorize or expose PII in training data
2. **Reduces attack surface**: Masked data has no value to attackers
3. **Protects users**: Prevents accidental disclosure of sensitive information
4. **Enables safe logging**: Logs can be stored/analyzed without exposing PII

### Real-World Scenarios

**Customer Support**:
```
User: "I can't log in. My email is john.doe@company.com and phone is 555-1234."
Masked: "I can't log in. My email is [EMAIL_REDACTED] and phone is [PHONE_REDACTED]."
```

**AI Response**:
```
AI: "According to our records, john.doe@company.com registered on..."
Masked: "According to our records, [EMAIL_REDACTED] registered on..."
```

## Component Responsibilities

The `PIIMaskingService` provides two main capabilities:

1. **Detection**: Identify PII in text and return structured information about matches
2. **Masking**: Replace PII with standardized placeholders

## Implementation

### Location
```
/src/main/java/com/techcorp/assistant/module05/security/PIIMaskingService.java
```

### Core Code

```java
@Service
public class PIIMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PIIMaskingService.class);

    // PII patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b([0-9]{3})-([0-9]{2})-([0-9]{4})\\b"
    );
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})\\b"
    );

    public String maskPII(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String masked = text;

        // Mask emails
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL_REDACTED]");

        // Mask phones
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE_REDACTED]");

        // Mask SSNs
        masked = SSN_PATTERN.matcher(masked).replaceAll("[SSN_REDACTED]");

        // Mask credit cards
        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("[CARD_REDACTED]");

        if (!masked.equals(text)) {
            log.debug("PII masked in text");
        }

        return masked;
    }

    public PIIDetectionResult detectPII(String text) {
        if (text == null || text.isBlank()) {
            return new PIIDetectionResult(false, List.of());
        }

        List<PIIMatch> matches = new ArrayList<>();

        // Detect emails
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            matches.add(new PIIMatch(
                    "EMAIL",
                    emailMatcher.group(),
                    emailMatcher.start(),
                    emailMatcher.end()
            ));
        }

        // Detect phones
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        while (phoneMatcher.find()) {
            matches.add(new PIIMatch(
                    "PHONE",
                    phoneMatcher.group(),
                    phoneMatcher.start(),
                    phoneMatcher.end()
            ));
        }

        // Detect SSNs
        Matcher ssnMatcher = SSN_PATTERN.matcher(text);
        while (ssnMatcher.find()) {
            matches.add(new PIIMatch(
                    "SSN",
                    ssnMatcher.group(),
                    ssnMatcher.start(),
                    ssnMatcher.end()
            ));
        }

        // Detect credit cards
        Matcher cardMatcher = CREDIT_CARD_PATTERN.matcher(text);
        while (cardMatcher.find()) {
            matches.add(new PIIMatch(
                    "CREDIT_CARD",
                    cardMatcher.group(),
                    cardMatcher.start(),
                    cardMatcher.end()
            ));
        }

        boolean containsPII = !matches.isEmpty();
        if (containsPII) {
            log.info("Detected {} PII instances in text", matches.size());
        }

        return new PIIDetectionResult(containsPII, matches);
    }

    public record PIIMatch(String type, String value, int start, int end) {}

    public record PIIDetectionResult(boolean containsPII, List<PIIMatch> matches) {}
}
```

## Pattern Analysis

### Email Pattern

```java
"\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
```

**Breakdown**:
- `\\b`: Word boundary (start)
- `[A-Za-z0-9._%+-]+`: Local part (before @) - letters, numbers, and common symbols
- `@`: Literal @ symbol
- `[A-Za-z0-9.-]+`: Domain name - letters, numbers, dots, hyphens
- `\\.`: Literal dot before TLD
- `[A-Z|a-z]{2,}`: Top-level domain (2+ letters)
- `\\b`: Word boundary (end)

**Matches**:
- `john.doe@example.com`
- `user+tag@subdomain.example.org`
- `test_email@company-name.co.uk`

**Doesn't match**:
- `invalid@` (no domain)
- `@example.com` (no local part)
- `test@example` (no TLD)

### Phone Pattern

```java
"\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
```

**Breakdown**:
- `(?:\\+?1[-.]?)?`: Optional country code (+1, 1-, 1.)
- `\\(?([0-9]{3})\\)?`: Area code (555) or 555
- `[-.]?`: Optional separator
- `([0-9]{3})`: Exchange code
- `[-.]?`: Optional separator
- `([0-9]{4})`: Line number

**Matches**:
- `555-123-4567`
- `(555) 123-4567`
- `5551234567`
- `+1-555-123-4567`
- `1.555.123.4567`

### SSN Pattern

```java
"\\b([0-9]{3})-([0-9]{2})-([0-9]{4})\\b"
```

**Breakdown**:
- `([0-9]{3})`: Area number (3 digits)
- `-`: Required hyphen
- `([0-9]{2})`: Group number (2 digits)
- `-`: Required hyphen
- `([0-9]{4})`: Serial number (4 digits)

**Matches**:
- `123-45-6789`

**Doesn't match**:
- `123456789` (no hyphens - intentionally strict for safety)
- `12-34-5678` (wrong format)

### Credit Card Pattern

```java
"\\b([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})[\\s-]?([0-9]{4})\\b"
```

**Breakdown**:
- `([0-9]{4})`: First 4 digits
- `[\\s-]?`: Optional space or hyphen
- Repeated for all 4 groups of 4 digits

**Matches**:
- `1234-5678-9012-3456`
- `1234 5678 9012 3456`
- `1234567890123456`

**Note**: This pattern doesn't validate card numbers (Luhn algorithm), only format.

## How It Works

### Masking Process

```java
String original = "Contact me at john@example.com or 555-1234";
String masked = piiMaskingService.maskPII(original);
// Result: "Contact me at [EMAIL_REDACTED] or [PHONE_REDACTED]"
```

**Processing steps**:
1. Check if text is null or blank (return as-is)
2. Apply email pattern replacement
3. Apply phone pattern replacement
4. Apply SSN pattern replacement
5. Apply credit card pattern replacement
6. Log if any masking occurred
7. Return masked text

**Order matters**: Patterns are applied sequentially. If patterns overlap (unlikely with these specific types), the first pattern wins.

### Detection Process

```java
PIIDetectionResult result = piiMaskingService.detectPII(
    "Email: user@test.com, Phone: 555-123-4567"
);

// result.containsPII() == true
// result.matches().size() == 2
// result.matches().get(0).type() == "EMAIL"
// result.matches().get(0).value() == "user@test.com"
// result.matches().get(0).start() == 7
// result.matches().get(0).end() == 20
```

**Use cases for detection**:
- **Alerting**: Warn users before submitting PII
- **Analytics**: Track how often PII appears in inputs
- **Selective masking**: Mask only certain types based on policy
- **Audit logging**: Record what PII was detected without storing the actual values

## Usage Examples

### In a REST Controller

```java
@PostMapping("/api/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    // Mask PII in user input before processing
    String maskedInput = piiMaskingService.maskPII(request.message());

    // Process with LLM
    String aiResponse = llmService.generate(maskedInput);

    // Mask PII in AI output before returning
    String maskedOutput = piiMaskingService.maskPII(aiResponse);

    return ResponseEntity.ok(new ChatResponse(maskedOutput));
}
```

### With Detection and Alerting

```java
public ProcessingResult processUserInput(String input) {
    PIIDetectionResult detection = piiMaskingService.detectPII(input);

    if (detection.containsPII()) {
        log.warn("PII detected in user input: {} instances", detection.matches().size());

        // Log details without exposing actual PII
        for (PIIMatch match : detection.matches()) {
            securityAuditService.logEvent(
                "PII_DETECTED",
                String.format("Type: %s at position %d-%d",
                    match.type(), match.start(), match.end())
            );
        }

        // Mask before processing
        String masked = piiMaskingService.maskPII(input);
        return process(masked);
    }

    return process(input);
}
```

### Selective Masking

```java
public String maskOnlyHighRiskPII(String text) {
    PIIDetectionResult result = piiMaskingService.detectPII(text);

    String masked = text;
    for (PIIMatch match : result.matches()) {
        // Only mask SSN and credit cards (high-risk)
        if (match.type().equals("SSN") || match.type().equals("CREDIT_CARD")) {
            String redaction = "[" + match.type() + "_REDACTED]";
            masked = masked.substring(0, match.start()) +
                     redaction +
                     masked.substring(match.end());
        }
    }
    return masked;
}
```

## Testing

### Unit Tests

Located at: `/src/test/java/com/techcorp/assistant/module05/security/PIIMaskingServiceTest.java`

**Key test cases**:

```java
@Test
void testMaskEmailAddress() {
    String text = "Contact me at john.doe@example.com for more info";
    String masked = service.maskPII(text);

    assertFalse(masked.contains("john.doe@example.com"));
    assertTrue(masked.contains("[EMAIL_REDACTED]"));
}

@Test
void testDetectMultiplePIITypes() {
    String text = "Email: user@test.com, Phone: 555-123-4567";
    PIIDetectionResult result = service.detectPII(text);

    assertTrue(result.containsPII());
    assertEquals(2, result.matches().size());

    long emailCount = result.matches().stream()
            .filter(m -> m.type().equals("EMAIL"))
            .count();
    assertEquals(1, emailCount);
}

@Test
void testPIIMatchIncludesPosition() {
    String text = "Contact: john@example.com";
    PIIDetectionResult result = service.detectPII(text);

    PIIMatch match = result.matches().get(0);
    assertEquals("EMAIL", match.type());
    assertEquals("john@example.com", match.value());
    assertTrue(match.start() >= 0);
    assertTrue(match.end() > match.start());
}
```

## Security Considerations

### Limitations

**Regex-based detection has blind spots**:
- **Obfuscation**: `john[dot]doe[at]example.com` won't be detected
- **Misspellings**: `johndoe@examplecom` (missing dot) won't match
- **International formats**: Non-US phone numbers might not match
- **Name detection**: No pattern for detecting person names
- **Address detection**: Street addresses not covered

**False positives**:
- Version numbers like `1.2.3.4` might match phone pattern
- Sequential numbers `1234-5678-9012-3456` match card pattern but aren't real cards
- Test data like `test@example.com` gets masked even though it's not real PII

**False negatives**:
- Real PII in unexpected formats gets missed
- Context-dependent PII (e.g., "my SSN is one two three...")

### Best Practices

1. **Use multiple detection methods**: Combine regex with NLP-based PII detection
2. **Allow configuration**: Let users specify which PII types to mask
3. **Preserve structure**: Consider masking like `j***@e***.com` instead of `[EMAIL_REDACTED]`
4. **Test with real data**: Validate patterns against actual PII from your domain
5. **Update patterns**: Add region-specific formats as needed

### Enhanced Patterns

For production systems, consider adding:

```java
// Names (basic)
Pattern.compile("\\b[A-Z][a-z]+ [A-Z][a-z]+\\b")

// IP addresses
Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")

// Street addresses (US)
Pattern.compile("\\b\\d{1,5}\\s+([A-Z][a-z]+\\s*){1,3}(Street|St|Avenue|Ave|Road|Rd|Drive|Dr)\\b")

// Dates of birth
Pattern.compile("\\b(0?[1-9]|1[0-2])/(0?[1-9]|[12][0-9]|3[01])/\\d{4}\\b")

// International phone (E.164)
Pattern.compile("\\+[1-9]\\d{1,14}\\b")
```

### Luhn Algorithm for Credit Cards

Add validation to reduce false positives:

```java
private boolean isValidCreditCard(String cardNumber) {
    String digits = cardNumber.replaceAll("[\\s-]", "");
    if (!digits.matches("\\d{13,19}")) return false;

    int sum = 0;
    boolean alternate = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
        int n = Integer.parseInt(digits.substring(i, i + 1));
        if (alternate) {
            n *= 2;
            if (n > 9) n -= 9;
        }
        sum += n;
        alternate = !alternate;
    }
    return (sum % 10 == 0);
}
```

## Performance Optimization

For high-throughput applications:

```java
// Pre-compile all patterns (already done with static final)
private static final Pattern EMAIL_PATTERN = ...

// Use single regex pass with named groups (Java 7+)
private static final Pattern COMBINED_PATTERN = Pattern.compile(
    "(?<email>\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b)|" +
    "(?<phone>\\b(?:\\+?1[-.]?)?\\(?[0-9]{3}\\)?[-.]?[0-9]{3}[-.]?[0-9]{4}\\b)|" +
    "(?<ssn>\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b)|" +
    "(?<card>\\b[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}[\\s-]?[0-9]{4}\\b)"
);

// Single pass detection and masking
public String maskPIIOptimized(String text) {
    StringBuffer result = new StringBuffer();
    Matcher matcher = COMBINED_PATTERN.matcher(text);

    while (matcher.find()) {
        String replacement = "[PII_REDACTED]";
        if (matcher.group("email") != null) replacement = "[EMAIL_REDACTED]";
        else if (matcher.group("phone") != null) replacement = "[PHONE_REDACTED]";
        else if (matcher.group("ssn") != null) replacement = "[SSN_REDACTED]";
        else if (matcher.group("card") != null) replacement = "[CARD_REDACTED]";

        matcher.appendReplacement(result, replacement);
    }
    matcher.appendTail(result);
    return result.toString();
}
```

---

**Next Chapter**: [04 - Output Validator](./04-output-validator.md)

**Related Topics**:
- [Prompt Injection Guard](./02-prompt-injection-guard.md) - Input security
- [Security Audit Service](./06-security-audit-service.md) - Logging PII events
