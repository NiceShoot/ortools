package com.example.ortools.autodivide.v2.autodividegroup.model;

import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AutoDivideParam {
    /**
     * 学校id
     */
    private Integer schoolId;
    /**
     * 管理部门
     */
    private String managementDeptCode;
    /**
     * 0-自动分组，1-插班自动分组，2-升年级预分组
     */
    private Integer divideType;
    /**
     * 生效时间
     */
    private String cardStartTime;
    /**
     * 失效时间
     */
    private String cardEndTime;
    /**
     * 当前学校、部门、生效时间、失效时间 条件下需要自动分组的会员卡
     */
    private List<LmMemberCardBaseInfoDTO> cardBaseInfoDTOListParam;
    /**
     * 升年级时使用的，获取失效时间大于这个时间的人数统计
     */
    private String effectDate;
    /**
     * key=会员卡编码，value=学生编码
     */
    private Map<String, String> cardStudentMap;
}
