package com.easybpm.sdk.model;

import java.util.List;

/** 分页响应，对应接口返回的 list/total/page/size。 */
public record PageResult<T>(List<T> list, long total, int page, int size) {

    public boolean isEmpty() {
        return list == null || list.isEmpty();
    }
}
