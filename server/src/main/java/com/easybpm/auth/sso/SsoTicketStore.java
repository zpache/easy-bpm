package com.easybpm.auth.sso;

import com.easybpm.auth.ConsoleAuthService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO 流程中的两类一次性票据。
 *
 * <p><b>state</b>：授权跳转前生成，回调时取出即删，用于防 CSRF，同时携带 nonce / PKCE verifier /
 * 登录后回跳路径；有效期 5 分钟。
 *
 * <p><b>ticket</b>：回调成功后生成，前端用它换本地 JWT；有效期 60 秒且一次性消费，
 * 避免把 token 拼在 URL 上（会被浏览器历史、Referer、网关日志记录）。
 *
 * <p><b>多实例部署注意</b>：当前是进程内存储，多副本部署需要换成 Redis 或改用签名 Cookie 承载，
 * 否则回调请求落到另一台实例会找不到 state。单实例部署（默认）无影响。
 */
@Component
public class SsoTicketStore {

    private static final long STATE_TTL_MS = 5 * 60_000L;
    private static final long TICKET_TTL_MS = 60_000L;
    private static final int CLEANUP_THRESHOLD = 500;

    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();
    private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    public record StateEntry(String tenantId, String providerId, String redirectPath,
                             String nonce, String codeVerifier, long expireAt) {
    }

    public record TicketEntry(ConsoleAuthService.LoginResult result, long expireAt) {
    }

    public void putState(String state, StateEntry entry) {
        cleanupIfNeeded();
        states.put(state, entry);
    }

    /** 取出即删：同一个 state 只能使用一次，重放会直接失败。 */
    public StateEntry consumeState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        StateEntry entry = states.remove(state);
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            return null;
        }
        return entry;
    }

    public void putTicket(String ticket, ConsoleAuthService.LoginResult result) {
        cleanupIfNeeded();
        tickets.put(ticket, new TicketEntry(result, System.currentTimeMillis() + TICKET_TTL_MS));
    }

    /** 取出即删：ticket 单次消费。 */
    public ConsoleAuthService.LoginResult consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            return null;
        }
        return entry.result();
    }

    private void cleanupIfNeeded() {
        if (states.size() + tickets.size() < CLEANUP_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        states.entrySet().removeIf(e -> now > e.getValue().expireAt());
        tickets.entrySet().removeIf(e -> now > e.getValue().expireAt());
    }
}
