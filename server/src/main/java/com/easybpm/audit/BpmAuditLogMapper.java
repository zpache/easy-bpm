package com.easybpm.audit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BpmAuditLogMapper extends BaseMapper<BpmAuditLog> {
}
