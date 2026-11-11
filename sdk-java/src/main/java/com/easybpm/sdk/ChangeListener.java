package com.easybpm.sdk;

import com.easybpm.sdk.model.ChangeEvent;

/**
 * 增量变更流回调。事件已按 eventId 去重，仍是「至少一次」语义：处理逻辑请保持幂等。
 */
public interface ChangeListener {

    void onEvent(ChangeEvent event);

    /** 轮询失败（网络/限流/服务端错误）时回调，默认忽略；轮询器会自动退避重试。 */
    default void onError(Exception error) {
    }
}
