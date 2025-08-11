package net.protsenko.fundy.notifier.service;

//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class TokenService {
//
//    private final RegTokenRepo repo;
//
//    public String createToken(Duration ttl) {
//        String uuid = UUID.randomUUID().toString();
//        repo.save(new RegToken(uuid, Instant.now(),
//                Instant.now().plus(ttl), false));
//        return uuid;
//    }
//
//    public String createDeepLink(String botUsername, Duration ttl) {
//        String token = createToken(ttl);
//        if (token.length() != 36) {
//            log.error("Generated token wrong length: {}", token);
//            throw new IllegalStateException("Bad token length");
//        }
//        return "https://t.me/%s?start=reg_%s".formatted(botUsername, token);
//    }
//}
