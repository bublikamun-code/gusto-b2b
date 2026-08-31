package by.gusto.auth.service;

import by.gusto.auth.entity.RecoveryCode;
import by.gusto.auth.entity.User;
import by.gusto.auth.repository.RecoveryCodeRepository;
import by.gusto.auth.repository.UserRepository;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TotpService {

    private static final int RECOVERY_CODE_LENGTH = 10;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int TOTP_VARIANCE = 1;

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TotpSetupResult generateSecret(User user) {
        if (user.isTotpEnabled()) {
            throw new GustoException(ErrorCode.AUTH_2FA_INVALID, "2FA уже включена");
        }
        String secret = TimeBasedOneTimePasswordUtil.generateBase32Secret();
        user.setTotpSecret(secret);
        userRepository.save(user);
        String otpauthUrl = "otpauth://totp/"
                + UriUtils.encode("GUSTO B2B:" + user.getEmail(), StandardCharsets.UTF_8)
                + "?secret=" + secret
                + "&issuer=" + UriUtils.encode("GUSTO B2B", StandardCharsets.UTF_8);
        return new TotpSetupResult(secret, otpauthUrl);
    }

    @Transactional
    public List<String> verifyAndEnable(User user, String code) {
        if (user.getTotpSecret() == null) {
            throw new GustoException(ErrorCode.AUTH_2FA_INVALID, "Сначала запросите секрет через /auth/2fa/enable");
        }
        if (!verifyCode(user, code)) {
            throw new GustoException(ErrorCode.AUTH_2FA_INVALID);
        }
        user.setTotpEnabled(true);
        userRepository.save(user);

        List<String> rawCodes = generateRecoveryCodes(RECOVERY_CODE_COUNT);
        List<RecoveryCode> entities = rawCodes.stream()
                .map(c -> RecoveryCode.builder()
                        .userId(user.getId())
                        .codeHash(sha256(c))
                        .used(false)
                        .build())
                .toList();
        recoveryCodeRepository.saveAll(entities);
        return rawCodes;
    }

    @Transactional(readOnly = true)
    public boolean verifyCode(User user, String code) {
        if (code == null || code.isBlank() || user.getTotpSecret() == null) {
            return false;
        }
        try {
            return TimeBasedOneTimePasswordUtil.validateCurrentNumber(user.getTotpSecret(), Integer.parseInt(code), TOTP_VARIANCE);
        } catch (NumberFormatException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    @Transactional
    public boolean verifyRecoveryCode(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String hash = sha256(code);
        Optional<RecoveryCode> opt = recoveryCodeRepository.findByUserIdAndCodeHash(user.getId(), hash);
        if (opt.isEmpty() || Boolean.TRUE.equals(opt.get().getUsed())) {
            return false;
        }
        RecoveryCode rc = opt.get();
        rc.setUsed(true);
        recoveryCodeRepository.save(rc);
        return true;
    }

    @Transactional
    public void disable(User user) {
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteAllByIdInBatch(
                recoveryCodeRepository.findAllByUserIdAndUsedFalse(user.getId()).stream()
                        .map(RecoveryCode::getId)
                        .toList());
    }

    private List<String> generateRecoveryCodes(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> RandomStringUtils.secure().nextAlphanumeric(RECOVERY_CODE_LENGTH, RECOVERY_CODE_LENGTH + 1))
                .toList();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record TotpSetupResult(String secret, String otpauthUrl) {
    }
}
