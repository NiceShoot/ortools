package com.example.ortools.自动分组.v2.autodividegroup.model;

import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmTeacherLevelPriorityDTO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AutoDivideDto {

    /**
     * 组剩余可排人数，库存
     */
    Map<String, Integer> groupStudentRemainNumMap;
    /**
     * 可用的组列表
     */
    private List<LmMemberCardBaseInfoDTO> cardBaseInfoDTOS;
    /**
     * 将会员卡入参 根据 产品、科目、年级、版本、层级 分组
     * key= 产品、科目、年级、版本、层级 分组 value=会员卡列表
     */
    private Map<String, List<LmMemberCardBaseInfoDTO>> teachingInfoCardMap;
    /**
     * 可用的组列表
     */
    private List<LmLearnGroupPO> lmLearnGroupPOListExceptZero;
    /**
     * key=产品，科目，年级，版本，层级 ; value = 组
     */
    private Map<String, List<LmLearnGroupPO>>teachingGroupMap;
    /**
     * 每个组的人数上限
     * key=groupCode ，value=最大人数上限
     */
    private Map<String, Integer> groupMaxStuNumMap;
    /**
     * 每个组的组长
     * key=groupCode，value=组长编码（老师code）
     */
    private Map<String, String> groupLeaderCodeMap;
    /**
     * 组带生量 key组编号，value组信息(组当前在读人数)
     */
    private Map<String, Integer> groupCurrentStuNumMap;
    /**
     * 查询所有老师的级别
     *
     * key=老师编码，value=级别（A、B、C、D、E、F）
     */
    private Map<String, String> teacherLevelMap;
    /**
     * 优先级，根据优先级顺序排序出来的优先级配置
     * key=优先级（有序），value=优先级配置
     */
    private Map<Integer, List<LmTeacherLevelPriorityDTO>> priorityConfigsGroupByMap;
    private List<LmTeacherLevelPriorityDTO> priorityConfigs;


}
