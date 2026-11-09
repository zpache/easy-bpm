package com.easybpm.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_org_user_role")
public class OrgUserRole {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userCode;
    private String roleCode;
    private LocalDateTime createdAt;
}
