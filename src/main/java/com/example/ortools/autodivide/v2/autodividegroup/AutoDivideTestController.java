package com.example.ortools.autodivide.v2.autodividegroup;

import cn.xdf.api.resource.management.AuToGroupOrToolsManager;
import cn.xdf.api.resource.management.bean.dto.ResponseBaseDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmTeacherLevelPriorityDTO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmConfigLevelRootPO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideDto;
import cn.xdf.api.resource.management.http.IBmHttpClient;
import cn.xdf.api.resource.management.manager.ILmLearnGroupManager;
import cn.xdf.api.resource.management.service.ILmConfigLevelPriorityService;
import cn.xdf.api.resource.management.service.ILmConfigLevelRootService;
import cn.xdf.api.resource.management.util.ConvertUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/encrypt/test")
public class AutoDivideTestController {

    @Autowired
    private ILmLearnGroupManager lmLearnGroupManager;
    @Autowired
    private IBmHttpClient bmHttpClient;
    @Autowired
    private AuToGroupOrToolsManager auToGroupOrToolsManager;
    @Autowired
    private ILmConfigLevelRootService lmConfigLevelRootService;
    @Autowired
    private ILmConfigLevelPriorityService lmConfigLevelPriorityService;
    @Autowired
    private AutoDivideGroupExecute autoDivideGroupExecute;


    @GetMapping("/or_tools/auto_divide3")
    public ResponseBaseDTO hello2(){

        AutoDivideDto autoDivideDto = new AutoDivideDto();

        /**
         * 组剩余可排人数，库存
         */
        Map<String, Integer> groupStudentRemainNumMap = new HashMap<>();
        groupStudentRemainNumMap.put("group_1",4);
        groupStudentRemainNumMap.put("group_2",20);
        autoDivideDto.setGroupStudentRemainNumMap(groupStudentRemainNumMap);
        /**
         * 将会员卡入参 根据 产品、科目、年级、版本、层级 分组
         * key= 产品、科目、年级、版本、层级 分组 value=会员卡列表
         */
        LmMemberCardBaseInfoDTO mc166200029038 = bmHttpClient.queryCardInfo(53, "MC166200029038");
        List<LmMemberCardBaseInfoDTO> list = new ArrayList<>();
        for (int i=0;i<15;i++){
            LmMemberCardBaseInfoDTO conversion = ConvertUtil.conversion(mc166200029038, LmMemberCardBaseInfoDTO.class);
            conversion.setMemberCode("card_"+i);
            list.add(conversion);
        }
        Map<String, List<LmMemberCardBaseInfoDTO>> teachingInfoCardMap = new HashMap<>();
        teachingInfoCardMap.put(AuToGroupOrToolsManager.FUN_LEARN_GROUP_SELECT_PARAM.apply(mc166200029038),list);
        autoDivideDto.setTeachingInfoCardMap(teachingInfoCardMap);
        autoDivideDto.setCardBaseInfoDTOS(list);
        /**
         * 可用的组列表
         */
        LmLearnGroupPO learnGroupPO = lmLearnGroupManager.queryByCode(53, "TM202205183673190053");
        List<LmLearnGroupPO> lmLearnGroupPOListExceptZero = new ArrayList<>();
        LmLearnGroupPO conversion = ConvertUtil.conversion(learnGroupPO, LmLearnGroupPO.class);
        conversion.setGroupCode("group_1");
        conversion.setGroupName("组1");
        lmLearnGroupPOListExceptZero.add(conversion);
        LmLearnGroupPO conversion2 = ConvertUtil.conversion(learnGroupPO, LmLearnGroupPO.class);
        conversion2.setGroupLeaderCode("GXH2274");
        conversion2.setGroupLeaderId("098730");
        conversion2.setGroupCode("group_2");
        conversion2.setGroupName("组2");
        lmLearnGroupPOListExceptZero.add(conversion2);
        autoDivideDto.setLmLearnGroupPOListExceptZero(lmLearnGroupPOListExceptZero);
        /**
         * key=产品，科目，年级，版本，层级 ; value = 组
         */
        Map<String, List<LmLearnGroupPO>>teachingGroupMap = lmLearnGroupPOListExceptZero.stream().collect(Collectors.groupingBy(AuToGroupOrToolsManager.FUN_LEARN_GROUP_SELECT_PO));
        autoDivideDto.setTeachingGroupMap(teachingGroupMap);
        /**
         * 每个组的人数上限
         * key=groupCode ，value=最大人数上限
         */
        Map<String, Integer> groupMaxStuNumMap = lmLearnGroupPOListExceptZero.stream()
                .collect(Collectors.toMap(LmLearnGroupPO::getGroupCode, LmLearnGroupPO::getMaxStuNum, (s1, s2) -> s1));
        autoDivideDto.setGroupMaxStuNumMap(groupMaxStuNumMap);
        /**
         * 每个组的组长
         * key=groupCode，value=组长编码（老师code）
         */
        Map<String, String> groupLeaderCodeMap = lmLearnGroupPOListExceptZero.stream()
                .collect(Collectors.toMap(LmLearnGroupPO::getGroupCode, LmLearnGroupPO::getGroupLeaderCode, (s1, s2) -> s1));
        autoDivideDto.setGroupLeaderCodeMap(groupLeaderCodeMap);
        /**
         * 组带生量 key组编号，value组信息(组当前在读人数)
         */
        Map<String, Integer> groupCurrentStuNumMap = new HashMap<>();
        groupCurrentStuNumMap.put("group_1",0);
        groupCurrentStuNumMap.put("group_2",0);
        autoDivideDto.setGroupCurrentStuNumMap(groupCurrentStuNumMap);
        /**
         * 查询所有老师的级别
         *
         * key=老师编码，value=级别（A、B、C、D、E、F）
         */
        Map<String, String> teacherLevelMap = new HashMap<>();
        teacherLevelMap.put("U202107231923004","A");
        teacherLevelMap.put("GXH2274","B");
        autoDivideDto.setTeacherLevelMap(teacherLevelMap);
        /**
         * 优先级，根据优先级顺序排序出来的优先级配置
         * key=优先级（有序），value=优先级配置
         */
        LmConfigLevelRootPO configLevelRootPO = lmConfigLevelRootService.queryByManagementDeptCode(53, "2200003439");//初中部
        if (configLevelRootPO == null || configLevelRootPO.getIsEnabledNew() == 0) {
            autoDivideDto.setPriorityConfigsGroupByMap(new HashMap<>(1));
        }
        List<LmTeacherLevelPriorityDTO> models = lmConfigLevelPriorityService.selectTeacherPriorityByIdPro(configLevelRootPO.getId());
        if (CollectionUtils.isEmpty(models)) {
            autoDivideDto.setPriorityConfigsGroupByMap(new HashMap<>(1));
        }
        Map<Integer, List<LmTeacherLevelPriorityDTO>> priorityConfigsGroupByMap = models.stream().sorted(Comparator.comparing(LmTeacherLevelPriorityDTO::getPriority)).collect(Collectors.groupingBy(LmTeacherLevelPriorityDTO::getPriority));
        autoDivideDto.setPriorityConfigsGroupByMap(priorityConfigsGroupByMap);
        autoDivideDto.setPriorityConfigs(models);
        Map<String, String> map = autoDivideGroupExecute.executeTest(autoDivideDto);
        return ResponseBaseDTO.builder().status("1").data(map).build();
    }



}
