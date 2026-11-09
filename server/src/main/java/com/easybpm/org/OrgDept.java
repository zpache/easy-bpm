package com.easybpm.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_org_dept")
public class OrgDept {

    @TableId(type = IdType.INPUT)
    private String id;
    private String deptCode;
    private String name;
    private String parentCode;
    private String leaderId;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
