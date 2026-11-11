package com.easybpm.sdk;

import com.easybpm.sdk.model.ChangeEvent;
import com.easybpm.sdk.model.ChangeFeed;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 增量变更流轮询器：定时拉 {@code /open/v1/changes}，按 eventId 去重后回调。
 *
 * <p>行为约定：
 * <ul>
 *   <li>{@code hasMore=true} 时立即续拉，不等下一个周期；</li>
 *   <li>失败按指数退避（interval 起，上限 maxBackoff），不丢游标，恢复后继续；</li>
 *   <li>本地保留最近 N 个 eventId（默认 2000，足够覆盖 3 秒重叠窗口），避免重复回调；</li>
 *   <li>单线程守护线程，{@link #close()} 停止并释放。</li>
 * </ul>
 */
public final class ChangePoller implements AutoCloseable {

    private final EasyBpmClient client;
    private final String userId;
    private final Duration interval;
    private final Duration maxBackoff;
    private final int limit;
    private final ChangeListener listener;
    private final ScheduledExecutorService scheduler;
    private final Set<String> seen;

    private volatile long cursor;
    private volatile boolean running;
    private volatile int consecutiveFailures;
    private ScheduledFuture<?> future;

    private ChangePoller(Builder builder) {
        this.client = builder.client;
        this.userId = builder.userId;
        this.cursor = builder.cursor == null ? 0L : builder.cursor;
        this.interval = builder.interval;
        this.maxBackoff = builder.maxBackoff;
        this.limit = builder.limit;
        this.listener = builder.listener;
        this.seen = Collections.newSetFromMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > builder.dedupeCapacity;
            }
        });
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "easy-bpm-poller-" + builder.userId);
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public static Builder builder(EasyBpmClient client, String userId, Long cursor) {
        return new Builder(client, userId, cursor);
    }

    /** 启动轮询（幂等，重复调用无副作用）。 */
    public ChangePoller start() {
        if (!running) {
            running = true;
            schedule(Duration.ZERO);
        }
        return this;
    }

    /** 暂停轮询，保留游标与去重集合。 */
    public void pause() {
        running = false;
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /** 当前游标，可用于持久化后下次从断点继续。 */
    public long cursor() {
        return cursor;
    }

    @Override
    public void close() {
        pause();
        scheduler.shutdownNow();
    }

    private void schedule(Duration delay) {
        if (!running) {
            return;
        }
        future = scheduler.schedule(this::tick, Math.max(delay.toMillis(), 0), TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running) {
            return;
        }
        try {
            ChangeFeed feed = client.changes(userId, cursor, limit);
            cursor = feed.cursor();
            consecutiveFailures = 0;
            for (ChangeEvent event : feed.events()) {
                if (seen.add(event.eventId())) {
                    listener.onEvent(event);
                }
            }
            schedule(feed.hasMore() ? Duration.ZERO : interval);
        } catch (RuntimeException e) {
            consecutiveFailures++;
            try {
                listener.onError(e);
            } catch (RuntimeException listenerError) {
                // 回调异常不能中断轮询
            }
            schedule(backoff());
        }
    }

    private Duration backoff() {
        long base = interval.toMillis() << Math.min(consecutiveFailures - 1, 5);
        return Duration.ofMillis(Math.min(base, maxBackoff.toMillis()));
    }

    /** 轮询器构建器。 */
    public static final class Builder {
        private final EasyBpmClient client;
        private final String userId;
        private final Long cursor;
        private Duration interval = Duration.ofSeconds(15);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private int limit = 50;
        private int dedupeCapacity = 2000;
        private ChangeListener listener = event -> {
        };

        private Builder(EasyBpmClient client, String userId, Long cursor) {
            this.client = client;
            this.userId = userId;
            this.cursor = cursor;
        }

        /** 空闲轮询间隔，默认 15 秒。 */
        public Builder interval(Duration interval) {
            this.interval = interval;
            return this;
        }

        /** 失败退避上限，默认 5 分钟。 */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        /** 单次拉取上限，默认 50（服务端上限 200）。 */
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        /** 本地去重容量，默认 2000。 */
        public Builder dedupeCapacity(int dedupeCapacity) {
            this.dedupeCapacity = dedupeCapacity;
            return this;
        }

        public Builder onEvent(ChangeListener listener) {
            this.listener = listener;
            return this;
        }

        public ChangePoller build() {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId 不能为空");
            }
            return new ChangePoller(this);
        }

        /** 构建并立即启动。 */
        public ChangePoller start() {
            return build().start();
        }
    }
}
