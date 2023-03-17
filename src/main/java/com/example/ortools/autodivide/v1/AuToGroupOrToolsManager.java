package com.example.ortools.autodivide.v1;

import cn.xdf.api.resource.management.bean.dto.CommonDto;
import cn.xdf.api.resource.management.bean.dto.learningmachine.*;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterGradeNumberV2DTO;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterLearnMachineGdProductDTO;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterSubjectDTO;
import cn.xdf.api.resource.management.bean.param.LmGroupTeachingParam;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmConfigLevelRootPO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmDivideGroupFailRecordPO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import cn.xdf.api.resource.management.bean.po.school.SchoolConfigPO;
import cn.xdf.api.resource.management.config.apollo.CloudOfficeConfig;
import cn.xdf.api.resource.management.dingding.DingDingService;
import cn.xdf.api.resource.management.enums.AutoDivideGroupErrorEnum;
import cn.xdf.api.resource.management.enums.AutoDivideGroupFailReasonEnum;
import cn.xdf.api.resource.management.enums.AutoDivideGroupTypeEnum;
import cn.xdf.api.resource.management.enums.TeacherGroupLevelEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideParam;
import cn.xdf.api.resource.management.http.ICourseCenterHttpClient;
import cn.xdf.api.resource.management.manager.*;
import cn.xdf.api.resource.management.service.ICloudOfficeService;
import cn.xdf.api.resource.management.service.ILmConfigLevelPriorityService;
import cn.xdf.api.resource.management.service.ILmConfigLevelRootService;
import cn.xdf.api.resource.management.threadpool.XdfThreadPoolExecutor;
import cn.xdf.api.resource.management.util.JwResourceLogger;
import cn.xdf.api.resource.management.util.JwResourceLoggerFactory;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service("auToGroupOrToolsManager")
public class AuToGroupOrToolsManager {

    private JwResourceLogger logger = JwResourceLoggerFactory.getLogger(AuToGroupOrToolsManager.class);

    @Autowired
    private ILmLearnGroupManager lmLearnGroupManager;
    @Autowired
    private ILmLearnGroupStudentManager lmLearnGroupStudentManager;
    @Autowired
    private ILmConfigLevelRootService lmConfigLevelRootService;
    @Autowired
    private ILmConfigLevelPriorityService lmConfigLevelPriorityService;
    @Autowired
    private DingDingService dingDingService;
    @Autowired
    private ICloudOfficeService cloudOfficeService;
    @Autowired
    private CloudOfficeConfig cloudOfficeConfig;
    @Autowired
    private ISchoolConfigManager schoolConfigManager;
    @Autowired
    private ICourseCenterHttpClient courseCenterHttpClient;
    @Autowired
    private ILmDivideGroupFailRecordManager lmDivideGroupFailRecordManager;
    @Autowired
    private ILmCourseProductCommonManager lmCourseProductCommonManager;


    // 学习组中的 产品，科目，年级，版本，层级
    public static Function<LmLearnGroupPO, String> FUN_LEARN_GROUP_SELECT_PO = t -> t.getInnerCategoryCode() + "," + t.getSubjectCode() + "," + t.getGradeCode() + "," + t.getBookCode() + "," + t.getDifficultyLevelCode();
    // 会员卡中的 产品，科目，年级，版本，层级
    public static Function<LmMemberCardBaseInfoDTO, String> FUN_LEARN_GROUP_SELECT_PARAM = t -> t.getProductId() + "," + t.getSubjectCode() + "," + t.getStudyGradeCode() + "," + t.getBookCode() + "," + t.getDifficultyLevelCode();


    /**
     * @param autoDivideParam
     * @return
     */
    public Map<String, String> autoDivideGroup(AutoDivideParam autoDivideParam) {

        // check入参
        if (!this.checkParam(autoDivideParam)) return new HashMap<>(1);
        // 初始化错误信息实体
        AutoDivideFailResultDto autoDivideFailResultDto = new AutoDivideFailResultDto();
        // 初始化智能分组入参实体
        AutoDivideDto autoDivideDto = new AutoDivideDto();
        // 固定参数
        Integer schoolId = autoDivideParam.getSchoolId();
        String managementDeptCode = autoDivideParam.getManagementDeptCode();
        // 将会员卡入参 根据 产品、科目、年级、版本、层级 分组
        autoDivideDto.setTeachingInfoCardMap(autoDivideParam.getCardBaseInfoDTOListParam().stream().collect(Collectors.groupingBy(FUN_LEARN_GROUP_SELECT_PARAM)));
        autoDivideDto.setCardBaseInfoDTOS(autoDivideParam.getCardBaseInfoDTOListParam());
        // 获取学习组列表 和  组库存
        this.queryAllGroupAndStock(autoDivideParam, autoDivideFailResultDto, autoDivideDto);
        // key=产品，科目，年级，版本，层级 ; value = 组编码列表
        CompletableFuture<Void> future = this.queryGroupListGrouping(autoDivideDto);
        // 每个组的人数上限
        CompletableFuture<Void> future1 = this.queryGroupMaxStuNum(autoDivideDto);
        // 每个组的组长
        CompletableFuture<Void> future2 = this.queryGroupLeader(autoDivideDto);
        // 组带生量 key组编号，value组信息(组当前在读人数)
        CompletableFuture<Void> future3 = this.queryGroupCurrentStuNum(autoDivideDto);
        // 查询所有老师的级别
        CompletableFuture<Void> future4 = this.queryTeacherLevel(schoolId, autoDivideDto);
        // 查询所有的优先级配置，根据优先级等级依次自动分配
        CompletableFuture<Void> future5 = this.queryPriority(schoolId, managementDeptCode, autoDivideDto);
        // join all future
        CompletableFuture.allOf(future,future1,future2,future3,future4,future5).join();
        // 给会员卡分配学习组，return=成功分组的卡组信息 key=会员卡编码，value=学习组编码
        //ConcurrentHashMap<String, String> cardAndGroupResultMap = this.autoDivideFun(autoDivideDto);
        ConcurrentHashMap<String, String> cardAndGroupResultMap = this.autoDivideFunPro(autoDivideDto);
        // 异步统计 异常信息
        this.failHandleFun(autoDivideParam, autoDivideFailResultDto, schoolId, cardAndGroupResultMap);
        return cardAndGroupResultMap;
    }

    /**
     * 失败数据处理
     * @param autoDivideParam
     * @param autoDivideFailResultDto
     * @param schoolId
     * @param cardAndGroupResultMap
     */
    private void failHandleFun(AutoDivideParam autoDivideParam, AutoDivideFailResultDto autoDivideFailResultDto, Integer schoolId, ConcurrentHashMap<String, String> cardAndGroupResultMap) {
        XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                // 1-未建组：前面代码已经 获得
                // 2-学习组已满
                ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> groupFullCardMap = autoDivideFailResultDto.getGroupFullCardMap();
                ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> nonExistentGroupCardMap = autoDivideFailResultDto.getNonExistentGroupCardMap();
                StringBuffer errorMsg = autoDivideFailResultDto.getErrorMsg();
                autoDivideParam.getCardBaseInfoDTOListParam().forEach(s->{
                    if (!cardAndGroupResultMap.containsKey(s.getMemberCode()) && !nonExistentGroupCardMap.containsKey(s.getMemberCode())){
                        groupFullCardMap.put(s.getMemberCode(),s);
                        errorMsg.append(String.format(AutoDivideGroupErrorEnum.B.getDesc(), schoolId,s.getProductId()
                                ,s.getSubjectCode(),s.getStudyGradeCode(),s.getBookCode(),s.getDifficultyLevelCode()));
                    }
                });
                // 云办公消息
                sendFailMsg(schoolId, autoDivideFailResultDto.getErrorMsg());
                // 存失败记录
                saveFailRecord(autoDivideParam, cardAndGroupResultMap, autoDivideFailResultDto);
            }
        });
    }


    /**
     * 保存 失败记录
     * @param autoDivideParam
     * @param cardAndGroupResultMap
     * @param autoDivideFailResultDto
     */
    private void saveFailRecord(AutoDivideParam autoDivideParam,
                                ConcurrentHashMap<String, String> cardAndGroupResultMap,
                                AutoDivideFailResultDto autoDivideFailResultDto) {
        Integer schoolId = autoDivideParam.getSchoolId();
        Integer divideType = autoDivideParam.getDivideType();
        Map<String, String> cardStudentMap = autoDivideParam.getCardStudentMap();
        // 异常数据
        ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> exceptionCardMap = autoDivideFailResultDto.getExceptionCardMap();
        ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> groupFullCardMap = autoDivideFailResultDto.getGroupFullCardMap();
        ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> nonExistentGroupCardMap = autoDivideFailResultDto.getNonExistentGroupCardMap();
        // 整理
        autoDivideParam.getCardBaseInfoDTOListParam().forEach(t->{
            String memberCode = t.getMemberCode();
            if(!nonExistentGroupCardMap.containsKey(memberCode) &&
                    !groupFullCardMap.containsKey(memberCode) &&
                    !cardAndGroupResultMap.containsKey(memberCode)){
                exceptionCardMap.put(memberCode,t);
            }
        });
        List<LmDivideGroupFailRecordPO> poList = Lists.newArrayList();
        if(Objects.nonNull(nonExistentGroupCardMap)&&!nonExistentGroupCardMap.isEmpty()){
            nonExistentGroupCardMap.forEach((cardCode,cardBaseInfoDTO)->{
                createFailRecordPO(schoolId, divideType, AutoDivideGroupFailReasonEnum.A.getStatus(), poList, cardCode, cardBaseInfoDTO,cardStudentMap);
            });
        }
        if(Objects.nonNull(groupFullCardMap)&&!groupFullCardMap.isEmpty()){
            groupFullCardMap.forEach((cardCode,cardBaseInfoDTO)->{
                createFailRecordPO(schoolId, divideType,AutoDivideGroupFailReasonEnum.B.getStatus(), poList, cardCode, cardBaseInfoDTO,cardStudentMap);
            });
        }
        if(Objects.nonNull(exceptionCardMap)&&!exceptionCardMap.isEmpty()){
            exceptionCardMap.forEach((cardCode,cardBaseInfoDTO)->{
                createFailRecordPO(schoolId, divideType,AutoDivideGroupFailReasonEnum.C.getStatus(), poList, cardCode, cardBaseInfoDTO,cardStudentMap);
            });
        }
        if(CollectionUtils.isNotEmpty(poList)){
            lmDivideGroupFailRecordManager.insertValues(poList);
        }
    }

    /**
     * 封装
     */
    private void createFailRecordPO(Integer schoolId, Integer divideType, Integer failReason,List<LmDivideGroupFailRecordPO> poList,
                                    String memberCode, LmMemberCardBaseInfoDTO cardBaseInfoDTO,Map<String,String> cardStudentMap) {
        LmDivideGroupFailRecordPO po = new LmDivideGroupFailRecordPO();
        po.setSchoolId(schoolId);
        po.setManagementDeptCode(cardBaseInfoDTO.getManageDeptCode());
        po.setDeptCode(cardBaseInfoDTO.getDeptCode());
        po.setInnerCategoryCode(cardBaseInfoDTO.getProductId());
        po.setSubjectCode(cardBaseInfoDTO.getSubjectCode());
        po.setGradeCode(cardBaseInfoDTO.getStudyGradeCode());
        po.setBookCode(cardBaseInfoDTO.getBookCode());
        po.setDifficultyLevelCode(cardBaseInfoDTO.getDifficultyLevelCode());
        po.setCardCode(memberCode);
        po.setStudentCode(cardStudentMap.get(memberCode));
        po.setDivideType(divideType);
        po.setFailReason(failReason);
        poList.add(po);
    }

    /**
     * 云办公消息
     */
    private void sendFailMsg(Integer schoolId, StringBuffer errorMsg) {
        try {
            logger.info("自动分组失败消息内容:{}", errorMsg);
            String excludeSchoolIds = cloudOfficeConfig.getExcludeSchoolIds();
            if(StringUtils.isNotBlank(excludeSchoolIds)){
                List<Integer> schoolIdList = Splitter.on(",").splitToList(excludeSchoolIds).stream().map(Integer::parseInt).collect(Collectors.toList());
                if(schoolIdList.contains(schoolId)){
                    return;
                }
            }
            //发送云办公消息
            SchoolConfigPO schoolConfigPO = schoolConfigManager.selectBySchoolId(schoolId);
            List<CourseCenterGradeNumberV2DTO> allGradeInner = courseCenterHttpClient.getAllGradeInner(schoolId);
            Map<String, String> gradeNameMap = allGradeInner.stream().collect(Collectors.toMap(CourseCenterGradeNumberV2DTO::getInner_code, CourseCenterGradeNumberV2DTO::getOutward_name, (s1, s2) -> s1));
            List<CourseCenterSubjectDTO> allSubject = courseCenterHttpClient.getAllSubject(null,new HashMap<>(4));
            Map<String, String> subjectNameMap = allSubject.stream().collect(Collectors.toMap(CourseCenterSubjectDTO::getCourse_product_subject_code, CourseCenterSubjectDTO::getSubject_name, (s1, s2) -> s1));
            List<CourseCenterLearnMachineGdProductDTO> learnMachineGdProductList = courseCenterHttpClient.getLearnMachineGdProductList(schoolId, null, null);
            List<CommonDto> bookList = lmCourseProductCommonManager.bookList(schoolId,0);
            Map<String, String> bookMap = bookList.stream().collect(Collectors.toMap(CommonDto::getValueCode, CommonDto::getValueName, (s1, s2) -> s1));
            List<CommonDto> difficultyLevelList = lmCourseProductCommonManager.difficultyLevelList();
            Map<String, String> difficultyLevelMap = difficultyLevelList.stream().collect(Collectors.toMap(CommonDto::getValueCode, CommonDto::getValueName, (s1, s2) -> s1));
            StringBuffer newMessage = new StringBuffer("");
            if (CollectionUtils.isNotEmpty(learnMachineGdProductList)){
                Map<String, String> productMap = learnMachineGdProductList.stream()
                        .collect(Collectors.toMap(CourseCenterLearnMachineGdProductDTO::getSchool_inner_category_code, CourseCenterLearnMachineGdProductDTO::getSchool_inner_category_name, (t1, t2) -> t1));
                List<String> msgList = Splitter.on("@").splitToList(errorMsg);
                msgList.forEach(m->{
                    List<String> mm = Splitter.on("#").splitToList(m);
                    if(CollectionUtils.isNotEmpty(mm)&&mm.size()==13){
                        newMessage.append(mm.get(0));
                        newMessage.append(schoolConfigPO.getSchoolName());
                        newMessage.append(mm.get(2));
                        newMessage.append(productMap.get(mm.get(3)));
                        newMessage.append(mm.get(4));
                        newMessage.append(subjectNameMap.get(mm.get(5)));
                        newMessage.append(mm.get(6));
                        newMessage.append(gradeNameMap.get(mm.get(7)));
                        newMessage.append(mm.get(8));
                        newMessage.append(bookMap.get(mm.get(9)));
                        newMessage.append(mm.get(10));
                        newMessage.append(difficultyLevelMap.get(mm.get(11)));
                        newMessage.append(mm.get(12));
                    }
                    if(CollectionUtils.isNotEmpty(mm)&&mm.size()==1){
                        newMessage.append(mm.get(0));
                    }
                });
            }
            if(StringUtils.isNotBlank(newMessage.toString())){
                cloudOfficeService.sendErrorMessageToCloudOfficeIm(newMessage.toString());
            }
        }catch (Exception e){
            logger.error("发送自动分组失败消息出错！消息内容:{}", errorMsg, e);
            cloudOfficeService.sendErrorMessageToCloudOfficeIm(errorMsg.toString());
        }
    }


    /**
     * 获取所有组数据  和  库存数据
     */
    private void queryAllGroupAndStock(AutoDivideParam autoDivideParam, AutoDivideFailResultDto autoDivideFailResultDto, AutoDivideDto autoDivideDto) {
        // 所有组数据
        List<LmLearnGroupPO> queryLearnGroupList = getLmLearnGroupPOS(autoDivideParam.getCardBaseInfoDTOListParam(), autoDivideParam.getSchoolId());
        // 根据会员卡的信息判断有没有对应的组，给出提示【未建组】
        this.checkCardNoExistGroup(autoDivideParam.getSchoolId(), autoDivideFailResultDto, autoDivideDto.getTeachingInfoCardMap(), queryLearnGroupList);
        // 查询组剩余可排人数，库存
        Map<String, Integer> groupStudentRemainNumMap = this.getGroupStockMap(autoDivideParam.getSchoolId(),
                autoDivideParam.getCardStartTime(), autoDivideParam.getCardEndTime(),
                autoDivideParam.getDivideType(), autoDivideParam.getEffectDate(), queryLearnGroupList);
        autoDivideDto.setGroupStudentRemainNumMap(groupStudentRemainNumMap);
        // 过滤掉库存为0的组
        List<LmLearnGroupPO> lmLearnGroupPOListExceptZero = queryLearnGroupList.stream().filter(t -> groupStudentRemainNumMap.getOrDefault(t.getGroupCode(),0)!= 0).collect(Collectors.toList());
        autoDivideDto.setLmLearnGroupPOListExceptZero(lmLearnGroupPOListExceptZero);
        // 清空缓存
        queryLearnGroupList.clear();
    }

    /**
     * key=产品，科目，年级，版本，层级 ; value = 组编码列表
     * @param autoDivideDto
     * @return
     */
    private CompletableFuture<Void> queryGroupListGrouping(AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                Map<String, List<LmLearnGroupPO>> teachingGroupMap = autoDivideDto.getLmLearnGroupPOListExceptZero().stream()
                        .collect(Collectors.groupingBy(FUN_LEARN_GROUP_SELECT_PO));
                autoDivideDto.setTeachingGroupMap(teachingGroupMap);
            }
        });
    }

    /**
     * 每个组的人数上限
     * @param autoDivideDto
     * @return
     */
    private CompletableFuture<Void> queryGroupMaxStuNum(AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                Map<String, Integer> groupMaxStuNumMap = autoDivideDto.getLmLearnGroupPOListExceptZero().stream()
                        .collect(Collectors.toMap(LmLearnGroupPO::getGroupCode, LmLearnGroupPO::getMaxStuNum, (s1, s2) -> s1));
                autoDivideDto.setGroupMaxStuNumMap(groupMaxStuNumMap);
            }
        });
    }

    /**
     * 每个组的组长
     * @param autoDivideDto
     * @return
     */
    private CompletableFuture<Void> queryGroupLeader(AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                Map<String, String> groupLeaderCodeMap = autoDivideDto.getLmLearnGroupPOListExceptZero().stream()
                        .collect(Collectors.toMap(LmLearnGroupPO::getGroupCode, LmLearnGroupPO::getGroupLeaderCode, (s1, s2) -> s1));
                autoDivideDto.setGroupLeaderCodeMap(groupLeaderCodeMap);
            }
        });
    }

    /**
     * 组带生量 key组编号，value组信息(组当前在读人数)
     * @param autoDivideDto
     * @return
     */
    private CompletableFuture<Void> queryGroupCurrentStuNum(AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                /**查询教师所在组带生量 key组编号，value组信息(组长，组最大人数，组当前人数)*/
                Map<String,Integer> groupCurrentStuNumMap = new HashMap<>();
                Map<String, Integer> groupStudentRemainNumMap = autoDivideDto.getGroupStudentRemainNumMap();
                autoDivideDto.getLmLearnGroupPOListExceptZero().forEach(t->{
                    Integer currentStudentNum = t.getMaxStuNum() - Optional.ofNullable(groupStudentRemainNumMap.get(t.getGroupCode())).orElse(0);
                    groupCurrentStuNumMap.put(t.getGroupCode(),currentStudentNum);
                });
                autoDivideDto.setGroupCurrentStuNumMap(groupCurrentStuNumMap);
            }
        });
    }

    /**
     * 查询所有老师的级别
     */
    private CompletableFuture<Void> queryTeacherLevel(Integer schoolId, AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                LmGroupTeachingParam lmGroupTeachingParam = new LmGroupTeachingParam();
                lmGroupTeachingParam.setSchoolId(schoolId);
                List<String> teacherCodeList = autoDivideDto.getLmLearnGroupPOListExceptZero().stream().map(LmLearnGroupPO::getGroupLeaderCode).collect(Collectors.toList());
                lmGroupTeachingParam.setTeacherCodeList(teacherCodeList);
                List<LmGroupTeachingDTO> teacherConfigList = lmConfigLevelRootService.queryTeacherConfigList(lmGroupTeachingParam);
                Map<String,String> teacherLevelMap = teacherConfigList.stream()
                        .collect(Collectors.toMap(LmGroupTeachingDTO::getTeacherCode,s-> TeacherGroupLevelEnum.getByCode(s.getLearningGroupLevel()).getDesc()));
                autoDivideDto.setTeacherLevelMap(teacherLevelMap);
            }
        });
    }

    /**
     * 查询所有的优先级配置，根据优先级等级依次自动分配
     */
    private CompletableFuture<Void> queryPriority(Integer schoolId, String managementDeptCode, AutoDivideDto autoDivideDto) {
        return XdfThreadPoolExecutor.runAsync(new Runnable() {
            @Override
            public void run() {
                LmConfigLevelRootPO configLevelRootPO = lmConfigLevelRootService.queryByManagementDeptCode(schoolId, managementDeptCode);
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
            }
        });
    }

    /**
     * 校验参数
     */
    private boolean checkParam(AutoDivideParam autoDivideParam) {
        logger.info("智能分组入参+"+ JSON.toJSONString(autoDivideParam));
        if (CollectionUtils.isEmpty(autoDivideParam.getCardBaseInfoDTOListParam())) {
            return false;
        }
        // 参数必填
        if (autoDivideParam.getSchoolId()==null ||
                autoDivideParam.getDivideType()==null ||
                StringUtils.isBlank(autoDivideParam.getManagementDeptCode()) ||
                StringUtils.isBlank(autoDivideParam.getCardEndTime()) ||
                StringUtils.isBlank(autoDivideParam.getCardStartTime()) ||
                autoDivideParam.getCardStudentMap().isEmpty()
        ){
            dingDingService.sendStartUpMsg("参数缺失");
            return false;
        }
        // 智能分组：学校、部门、生效时间、失效时间 不一致
        List<LmMemberCardBaseInfoDTO> cardBaseInfoDTOListParam = autoDivideParam.getCardBaseInfoDTOListParam();
        LmMemberCardBaseInfoDTO temp = cardBaseInfoDTOListParam.get(0);
        for (LmMemberCardBaseInfoDTO dto : cardBaseInfoDTOListParam){
            if (!dto.getSchoolId().equals(temp.getSchoolId()) ||
                    !dto.getManageDeptCode().equals(temp.getManageDeptCode()) ||
                    !dto.getEffectiveStartTime().equals(temp.getEffectiveStartTime()) ||
                    !dto.getEffectiveEndTime().equals(temp.getEffectiveEndTime())
            ){
                dingDingService.sendStartUpMsg("智能分组：学校、部门、生效时间、失效时间 不一致");
                return false;
            }
        }

        return true;
    }

    /**
     * 分组逻辑
     * @param autoDivideDto
     * @return
     */
    public ConcurrentHashMap<String, String> autoDivideFunPro(AutoDivideDto autoDivideDto) {

        // 入组结果 key=会员卡，value=组编码
        ConcurrentHashMap<String, String> cardAndGroupResultMap = new ConcurrentHashMap<>();

        // ----------------------------入参
        List<LmMemberCardBaseInfoDTO> cardBaseInfoList = autoDivideDto.getCardBaseInfoDTOS();
        List<LmLearnGroupPO> lmLearnGroupPOS = autoDivideDto.getLmLearnGroupPOListExceptZero();
        Map<String, Integer> groupMaxStuNumMap = autoDivideDto.getGroupMaxStuNumMap();
        Map<String, String> groupLeaderCodeMap = autoDivideDto.getGroupLeaderCodeMap();
        Map<String, Integer> groupCurrentStuNumMap = autoDivideDto.getGroupCurrentStuNumMap();
        Map<String, String> teacherLevelMap = autoDivideDto.getTeacherLevelMap();
        Map<String, Integer> groupStudentRemainNumMap = autoDivideDto.getGroupStudentRemainNumMap();
        List<LmTeacherLevelPriorityDTO> priorityConfigs = autoDivideDto.getPriorityConfigs();

        // ----------------------------入参转换
        // 编码列表
        List<String> groupCodesDist = new ArrayList<>();
        List<String> productCodes = new ArrayList<>();
        List<String> subjectCodes = new ArrayList<>();
        List<String> gradeCodes = new ArrayList<>();
        List<String> bookCodes = new ArrayList<>();
        List<String> difficultyLevelCodes = new ArrayList<>();

        lmLearnGroupPOS.forEach(s->{
            if (!groupCodesDist.contains(s.getGroupCode()))groupCodesDist.add(s.getGroupCode());
            if (!productCodes.contains(s.getInnerCategoryCode()))productCodes.add(s.getInnerCategoryCode());
            if (!subjectCodes.contains(s.getSubjectCode()))subjectCodes.add(s.getSubjectCode());
            if (!gradeCodes.contains(s.getGradeCode()))gradeCodes.add(s.getGradeCode());
            if (!bookCodes.contains(s.getBookCode()))bookCodes.add(s.getBookCode());
            if (!difficultyLevelCodes.contains(s.getDifficultyLevelCode()))difficultyLevelCodes.add(s.getDifficultyLevelCode());
        });
        // 优先级
        List<Integer> priorities = priorityConfigs.stream().map(LmTeacherLevelPriorityDTO::getPriority).distinct().sorted().collect(Collectors.toList());
        // 档位
        List<Integer> indexes = priorityConfigs.stream().map(LmTeacherLevelPriorityDTO::getIndexA).distinct().collect(Collectors.toList());
        Map<Integer, Pair<Integer, Integer>> lineIndexMap = priorityConfigs.stream().collect(Collectors.toMap(LmTeacherLevelPriorityDTO::getIndexA, s -> Pair.of(s.getLineIndex(), s.getNextLineIndex()), (s1, s2) -> s1));
        // 级别
        List<Integer> levels = Arrays.asList(1,2,3,4,5,6);
        // key=优先级，value=优先级配置
        Map<String, List<LmTeacherLevelPriorityDTO>> priorityConfigsGroupByLevelMap = priorityConfigs.stream().collect(Collectors.groupingBy(s -> s.getTeacherLevel()));
        // key=老师级别，value=优先级码列表
        Map<String, List<Integer>> levelAndPriorityMap = priorityConfigs.stream().collect(Collectors.groupingBy(LmTeacherLevelPriorityDTO::getTeacherLevel, Collectors.mapping(LmTeacherLevelPriorityDTO::getPriority, Collectors.toList())));
        // key = groupCode+"_"+p.getTeacherLevel()+"_"+p.getPriority()+"_"+p.getIndexA(); value= 档位的 下限和上限
        Map<String,Pair<Integer,Integer>> groupLevelPriorityLineMap = new HashMap<>();
        lmLearnGroupPOS.forEach(group -> {
            String groupCode = group.getGroupCode();
            String teacherCode = groupLeaderCodeMap.get(groupCode);
            String levelStr = teacherLevelMap.getOrDefault(teacherCode,"F");
            if (priorityConfigsGroupByLevelMap.containsKey(levelStr)){
                List<LmTeacherLevelPriorityDTO> lmTeacherLevelPriorityDTOS = priorityConfigsGroupByLevelMap.get(levelStr);
                int size = lmTeacherLevelPriorityDTOS.size();
                for (int i=0;i<size;i++){
                    LmTeacherLevelPriorityDTO p = lmTeacherLevelPriorityDTOS.get(i);
                    if (levelStr.equals(p.getTeacherLevel())) {
                        String str = groupCode+"_"+p.getTeacherLevel()+"_"+p.getPriority()+"_"+p.getIndexA();
                        if (i==size-1){
                            groupLevelPriorityLineMap.put(str,Pair.of(p.getLineIndex(),999999));
                        }else {
                            groupLevelPriorityLineMap.put(str,Pair.of(p.getLineIndex(),p.getNextLineIndex()));
                        }
                    }
                }
            }
        });



        // key=产品+科目+年级+教材+层级+优先级+档位
        Map<String,List<Integer>> priorityAndIndexGroupListMap = new HashMap<>();
        for (int k=0;k<lmLearnGroupPOS.size();k++){
            LmLearnGroupPO lmLearnGroupPO = lmLearnGroupPOS.get(k);
            String groupCode = lmLearnGroupPO.getGroupCode();
            String teacherCode = groupLeaderCodeMap.get(groupCode);
            String levelStr = teacherLevelMap.getOrDefault(teacherCode,"F");
            if (priorityConfigsGroupByLevelMap.containsKey(levelStr)){
                List<LmTeacherLevelPriorityDTO> lmTeacherLevelPriorityDTOS = priorityConfigsGroupByLevelMap.get(levelStr);
                int size = lmTeacherLevelPriorityDTOS.size();
                for (int i=0;i<size;i++){
                    LmTeacherLevelPriorityDTO p = lmTeacherLevelPriorityDTOS.get(i);
                    if (levelStr.equals(p.getTeacherLevel())) {
                        String str = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPO) +","+p.getPriority()+","+p.getIndexA();
                        List<Integer> list = priorityAndIndexGroupListMap.getOrDefault(str, new ArrayList<>());
                        list.add(k);
                        priorityAndIndexGroupListMap.put(str,list);
                    }
                }
            }
        }

        // ----------------------------基础数据
        int groupSize = lmLearnGroupPOS.size();
        int cardSize = cardBaseInfoList.size();
        int prioritySize = priorities.size();
        int indexSize = indexes.size();
        int levelSize = levels.size();
        int productSize = productCodes.size();
        int subjectSize = subjectCodes.size();
        int gradeSize = gradeCodes.size();
        int bookSize = bookCodes.size();
        int difficultyLevelSize = difficultyLevelCodes.size();

        // ----------------------------初始化数组
        int[] groupArr = IntStream.range(0, groupSize).toArray();
        int[] cardCodeArr = IntStream.range(0, cardSize).toArray();
        int[] priorityArr = IntStream.range(0, prioritySize).toArray();
        int[] indexArr = IntStream.range(0, indexSize).toArray();
        int[] levelArr = IntStream.range(0, levelSize).toArray();
        int[] productArr = IntStream.range(0, productSize).toArray();
        int[] subjectArr = IntStream.range(0, subjectSize).toArray();
        int[] gradeArr = IntStream.range(0, gradeSize).toArray();
        int[] bookArr = IntStream.range(0, bookSize).toArray();
        int[] difficultyLevelArr = IntStream.range(0, difficultyLevelSize).toArray();

        // ----------------------------数组角标与实际值对应关系
        Map<Integer,Integer> groupStockIndexMap = new HashMap<>();
        Map<Integer,Integer> groupMaxStuIndexMap = new HashMap<>();
        Map<Integer,Integer> groupCurrentStuIndexMap = new HashMap<>();
        Map<Integer,Integer> groupLevelIndexMap = new HashMap<>();
        for (int i=0;i<groupSize;i++) {
            String groupCode = lmLearnGroupPOS.get(i).getGroupCode();
            groupStockIndexMap.put(i, groupStudentRemainNumMap.getOrDefault(groupCode,0));
            groupMaxStuIndexMap.put(i, groupMaxStuNumMap.getOrDefault(groupCode,0));
            groupCurrentStuIndexMap.put(i, groupCurrentStuNumMap.getOrDefault(groupCode,0));
            groupLevelIndexMap.put(i,TeacherGroupLevelEnum.getByDesc(teacherLevelMap.getOrDefault(groupLeaderCodeMap.get(groupCode),"F")).getCode());
        }

        // ----------------------------系数限制
        // 根据优先级设置系数
        Map<Integer,Integer> priorityCoeffMap = new HashMap<>();
        int priorityCoeff = prioritySize;
        for (int i=0;i<prioritySize;i++) {
            priorityCoeffMap.put(i,priorityCoeff);
            priorityCoeff -- ;
        }

        // 所有组的总库存
        //int totalStock = groupStudentRemainNumMap.values().stream().mapToInt(s -> s).sum();
        // 所有组的总最大容量
        //int totalMaxStuNum = groupMaxStuNumMap.keySet().stream().filter(groupCodesDist::contains).mapToInt(groupMaxStuNumMap::get).sum();
        // 所有组的总在读人数
        //int totalCurrentStuNum = groupCurrentStuNumMap.keySet().stream().filter(groupCodesDist::contains).mapToInt(groupCurrentStuNumMap::get).sum();

        // ----------------------------or-tools
        Loader.loadNativeLibraries();
        // 组建模型
        CpModel cpModel = new CpModel();
        // 所有点位
        Literal[][][][][][][][][][] cardGroupPoints = new Literal[cardSize][groupSize][prioritySize][indexSize][levelSize][productSize][subjectSize][gradeSize][bookSize][difficultyLevelSize];
        for (Integer card : cardCodeArr){
            for (Integer group : groupArr){
                for (Integer priority : priorityArr){
                    for (Integer level : levelArr){
                        for (Integer product : productArr){
                            for (Integer subject : subjectArr){
                                for (Integer grade : gradeArr){
                                    for (Integer book : bookArr){
                                        for (Integer difficulty : difficultyLevelArr){
                                            for (Integer index : indexArr){
                                                cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty] =
                                                        cpModel.newBoolVar(card+","+group+","+priority+","+index+","+level+","+product+","+subject+","+grade+","+book+","+difficulty);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1-每张卡正好进入一个组 (每个组只有一个级别，每个级别只有几个优先级)
        // 2-根据优先级和老师级别设置系数
        for (Integer card : cardCodeArr){
            String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
            LinearExprBuilder builder1 = LinearExpr.newBuilder();
            LinearExprBuilder builder2 = LinearExpr.newBuilder();
            for (Integer group : groupArr){
                String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group));
                if (cardKey.equals(groupKey)){
                    Integer thisGroupLevel = groupLevelIndexMap.get(group);
                    for (Integer level : levelArr){
                        if (thisGroupLevel.equals(levels.get(level))){
                            List<Integer> priorityList = levelAndPriorityMap.get(TeacherGroupLevelEnum.getByCode(level + 1).getDesc());
                            for (Integer priority : priorityArr){
                                if (priorityList.contains(priorities.get(priority))){
                                    Integer priorityCo = priorityCoeffMap.get(priority);
                                    for (Integer product : productArr){
                                        for (Integer subject : subjectArr){
                                            for (Integer grade : gradeArr){
                                                for (Integer book : bookArr){
                                                    for (Integer difficulty : difficultyLevelArr){
                                                        for (Integer index : indexArr){
                                                            builder1.addTerm(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty],1);
                                                            builder2.addTerm(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty],(priorityCo)*100);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            cpModel.addEquality(builder1,1);
            maximize(cpModel,builder2);
        }

        // 组人数上限 根据库存设置
        for (Integer group : groupArr){
            String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group));
            LinearExprBuilder builder = LinearExpr.newBuilder();
            for (Integer card : cardCodeArr){
                String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
                if (cardKey.equals(groupKey)){
                    for (Integer level : levelArr){
                        for (Integer priority : priorityArr){
                            for (Integer product : productArr){
                                for (Integer subject : subjectArr){
                                    for (Integer grade : gradeArr){
                                        for (Integer book : bookArr){
                                            for (Integer difficulty : difficultyLevelArr){
                                                for (Integer index : indexArr){
                                                    builder.addTerm(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty],1);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            }
            cpModel.addLessOrEqual(builder,groupStockIndexMap.get(group));
        }

        // 限制每个档位的人数
        for (Integer group : groupArr){
            String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group));
            Integer currentStuNum = groupCurrentStuIndexMap.get(group);
            for (Integer level : levelArr){
                for (Integer priority : priorityArr){
                    for (Integer index : indexArr){
                        LinearExprBuilder builder = LinearExpr.newBuilder();
                        for (Integer card : cardCodeArr){
                            String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
                            if (cardKey.equals(groupKey)){
                                for (Integer product : productArr){
                                    for (Integer subject : subjectArr){
                                        for (Integer grade : gradeArr){
                                            for (Integer book : bookArr){
                                                for (Integer difficulty : difficultyLevelArr){
                                                    builder.add(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty]);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        String thiskey = lmLearnGroupPOS.get(group).getGroupCode()+"_"+
                                TeacherGroupLevelEnum.getByCode(levels.get(level)).getDesc()+"_"+
                                priorities.get(priority)+"_"+
                                indexes.get(index);
                        if (groupLevelPriorityLineMap.containsKey(thiskey)){
                            Pair<Integer, Integer> pair = groupLevelPriorityLineMap.get(thiskey);
                            Integer left = pair.getLeft();
                            Integer right = pair.getRight();
                            if (currentStuNum<=left){
                                cpModel.addLessOrEqual(builder,right-left);
                            }
                            if (currentStuNum>left && currentStuNum<right){
                                cpModel.addLessOrEqual(builder,right-currentStuNum);
                            }
                            if (currentStuNum >= right){
                                cpModel.addLessOrEqual(builder,0);
                            }
                        }else {
                            cpModel.addLessOrEqual(builder,0);
                        }
                    }
                }
            }
        }

        // 每个组必须前面一个档位人满之后再进入后面的档位
        for (Integer group : groupArr){
            String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group));
            Integer currentStuNum = groupCurrentStuIndexMap.get(group);
            for (Integer level : levelArr){
                for (int index=0;index<indexSize-1;index++){
                    LinearExprBuilder builder1 = LinearExpr.newBuilder();
                    LinearExprBuilder builder2 = LinearExpr.newBuilder();
                    for (Integer card : cardCodeArr){
                        String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
                        if (cardKey.equals(groupKey)){
                            for (Integer priority : priorityArr){
                                for (Integer product : productArr){
                                    for (Integer subject : subjectArr){
                                        for (Integer grade : gradeArr){
                                            for (Integer book : bookArr){
                                                for (Integer difficulty : difficultyLevelArr){
                                                    builder1.add(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty]);
                                                    builder2.add(cardGroupPoints[card][group][priority][index+1][level][product][subject][grade][book][difficulty]);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Pair<Integer, Integer> pair1 = lineIndexMap.get(index);
                    Integer right = pair1.getRight();
                    Integer left = pair1.getLeft();
                    Integer temp = 0;
                    if (currentStuNum<=left){
                        temp = right - left;
                    }
                    if (currentStuNum>left && currentStuNum<right){
                        temp = right - currentStuNum;
                    }
                    Literal literal = cpModel.newBoolVar( "变量");
                    cpModel.addEquality(builder1,temp).onlyEnforceIf(literal);
                    cpModel.addGreaterOrEqual(builder2,0).onlyEnforceIf(literal);
                    cpModel.addLessOrEqual(builder1,temp).onlyEnforceIf(literal.not());
                    cpModel.addEquality(builder2,0).onlyEnforceIf(literal.not());
                }
            }
        }

        // 均匀散列 同产品科目年级教材层级档位 优先级 的 每个组的人数 均匀
        for (Integer product : productArr){
            for (Integer subject : subjectArr){
                for (Integer grade : gradeArr){
                    for (Integer book : bookArr){
                        for (Integer difficulty : difficultyLevelArr){
                            for (Integer index : indexArr){
                                for (Integer priority : priorityArr){
                                    String thiskey = productCodes.get(product)+","+subjectCodes.get(subject)+","+gradeCodes.get(grade)+","+bookCodes.get(book)+
                                            ","+difficultyLevelCodes.get(difficulty)+","+priorities.get(priority)+","+indexes.get(index);
                                    List<Integer> list = priorityAndIndexGroupListMap.getOrDefault(thiskey,new ArrayList<>(1));
                                    if (list.size()>=2){

                                    }
                                    // 总人数
                                    LinearExprBuilder builder = LinearExpr.newBuilder();
                                    for (int x=0;x<list.size()-1;x++){
                                        Integer group = list.get(x);
                                        String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group));
                                        for (Integer card : cardCodeArr){
                                            String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
                                            if (cardKey.equals(groupKey)){
                                                for (Integer level : levelArr){
                                                    builder.add(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty]);
                                                }
                                            }
                                        }
                                    }

                                    for (int x=0;x<list.size()-1;x++){
                                        Integer group1 = list.get(x);
                                        Integer group2 = list.get(x+1);
                                        LinearExprBuilder builder1 = LinearExpr.newBuilder();
                                        LinearExprBuilder builder2 = LinearExpr.newBuilder();
                                        String groupKey = FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPOS.get(group1));
                                        for (Integer card : cardCodeArr){
                                            String cardKey = FUN_LEARN_GROUP_SELECT_PARAM.apply(cardBaseInfoList.get(card));
                                            if (cardKey.equals(groupKey)){
                                                for (Integer level : levelArr){
                                                    builder1.add(cardGroupPoints[card][group1][priority][index][level][product][subject][grade][book][difficulty]);
                                                    builder2.add(cardGroupPoints[card][group2][priority][index][level][product][subject][grade][book][difficulty]);
                                                }
                                            }
                                        }

                                        Pair<Integer, Integer> pair = lineIndexMap.get(index);
                                        Integer right = pair.getRight();
                                        Integer left = pair.getLeft();

                                        // group 1
                                        Integer currentStuNum1 = groupCurrentStuIndexMap.get(group1);
                                        Integer stock1 = groupStockIndexMap.get(group1);
                                        Integer right1 = Math.min(right,stock1);
                                        Integer left1 = Math.max(left,currentStuNum1);
                                        Integer temp1 = currentStuNum1 > right1 ? 0 : right1-left1;

                                        // group 2
                                        Integer currentStuNum2 = groupCurrentStuIndexMap.get(group2);
                                        Integer stock2 = groupStockIndexMap.get(group2);
                                        Integer right2 = Math.min(right,stock2);
                                        Integer left2 = Math.max(left,currentStuNum2);
                                        Integer temp2 = currentStuNum1 > right2 ? 0 : right2-left2;

                                        // 差值
                                        if (temp2>temp1){
                                            builder2.addTerm(builder1,-1);
                                            cpModel.addLinearConstraint(builder2,0,Math.abs(temp2-temp1));
                                        }
                                        else if (temp2==temp1){
                                            builder2.addTerm(builder1,-1);
                                            cpModel.addLinearConstraint(builder2,0,1);
                                        }
                                        else {
                                            builder1.addTerm(builder2,-1);
                                            cpModel.addLinearConstraint(builder1,0,Math.abs(temp2-temp1));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        // 求解
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(cpModel);
        // 没有排上的会员卡
        List<LmMemberCardBaseInfoDTO> noGroupCardList = new ArrayList<>();
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (int card : cardCodeArr){
                LmMemberCardBaseInfoDTO lmMemberCardBaseInfoDTO = cardBaseInfoList.get(card);
                String memberCode = lmMemberCardBaseInfoDTO.getMemberCode();
                boolean flag = true;
                for (int group : groupArr) {
                    LmLearnGroupPO lmLearnGroupPO = lmLearnGroupPOS.get(group);
                    for (Integer priority : priorityArr){
                        for (Integer level : levelArr){
                            for (Integer product : productArr){
                                for (Integer subject : subjectArr){
                                    for (Integer grade : gradeArr){
                                        for (Integer book : bookArr){
                                            for (Integer difficulty : difficultyLevelArr){
                                                for (Integer index : indexArr){
                                                    if (solver.booleanValue(cardGroupPoints[card][group][priority][index][level][product][subject][grade][book][difficulty])) {
                                                        Pair<Integer, Integer> pair = lineIndexMap.get(index);
                                                        // 记录入组的卡关系
                                                        cardAndGroupResultMap.put(memberCode,lmLearnGroupPO.getGroupCode());
                                                        flag = false;
//                                                        logger.info("会员卡 {} 学习大组 {} 级别 {} 优先级 {} 产品 {} 科目 {} 年级 {} 教材 {} 层级 {} 人数档位{}-{}.%n",
//                                                                memberCode,lmLearnGroupPO.getGroupCode(),TeacherGroupLevelEnum.getByCode(levels.get(level)).getDesc(),priorities.get(priority),
//                                                                productCodes.get(product),subjectCodes.get(subject),gradeCodes.get(grade),bookCodes.get(book),difficultyLevelCodes.get(difficulty),
//                                                                pair.getLeft(),pair.getRight());
                                                        logger.info("会员卡 {} 学习大组 {} 级别 {} 优先级 {}  .%n",
                                                                memberCode,lmLearnGroupPO.getGroupCode(),TeacherGroupLevelEnum.getByCode(levels.get(level)).getDesc(),priorities.get(priority));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (flag)noGroupCardList.add(lmMemberCardBaseInfoDTO);
            }
        } else {
            logger.info("没有方案");
        }
        logger.info("没有分组的卡："+JSON.toJSONString(noGroupCardList));
        return cardAndGroupResultMap;
    }

    /**
     * 分组逻辑
     * @param autoDivideDto
     * @return
     */
    public ConcurrentHashMap<String, String> autoDivideFun(AutoDivideDto autoDivideDto) {
        // 入组结果 key=会员卡，value=组编码
        ConcurrentHashMap<String, String> cardAndGroupResultMap = new ConcurrentHashMap<>();
        // 入参
        Map<String, List<LmMemberCardBaseInfoDTO>> teachingInfoCardMap =autoDivideDto.getTeachingInfoCardMap();
        Map<String, List<LmLearnGroupPO>> teachingGroupMap =autoDivideDto.getTeachingGroupMap();
        Map<String, Integer> groupMaxStuNumMap =autoDivideDto.getGroupMaxStuNumMap();
        Map<String, String> groupLeaderCodeMap =autoDivideDto.getGroupLeaderCodeMap();
        Map<String, Integer> groupCurrentStuNumMap =autoDivideDto.getGroupCurrentStuNumMap();
        Map<String, String> teacherLevelMap =autoDivideDto.getTeacherLevelMap();
        Map<String, Integer> groupStudentRemainNumMap = autoDivideDto.getGroupStudentRemainNumMap();
        List<LmTeacherLevelPriorityDTO> priorityConfigs = autoDivideDto.getPriorityConfigs();
        // 入组逻辑
        teachingInfoCardMap.keySet().parallelStream().forEach(key->{
            // 需要分组的卡
            List<LmMemberCardBaseInfoDTO> cardBaseInfoList = teachingInfoCardMap.get(key);
            // 需要分组的组
            List<LmLearnGroupPO> lmLearnGroupPOS = teachingGroupMap.getOrDefault(key,new ArrayList<>(1));
            List<String> groupCodes = lmLearnGroupPOS.stream().map(LmLearnGroupPO::getGroupCode).distinct().collect(Collectors.toList());
            // 优先级个数
            int priorityCount = priorityConfigs.stream().mapToInt(LmTeacherLevelPriorityDTO::getPriority).max().getAsInt();
            // 级别个数
            int levelCount = 6;
            // 优先级配置根据级别分组
            Map<String, List<LmTeacherLevelPriorityDTO>> priorityConfigsGroupByLevelMap = priorityConfigs.stream().collect(Collectors.groupingBy(s -> s.getTeacherLevel()));
            Map<String, List<Integer>> levelAndPriorityMap = priorityConfigs.stream().collect(Collectors.groupingBy(LmTeacherLevelPriorityDTO::getTeacherLevel, Collectors.mapping(LmTeacherLevelPriorityDTO::getPriority, Collectors.toList())));
            // 每个组_级别_优先级 下可以 入多少人
            // key = groupCode+"_"+p.getTeacherLevel()+"_"+p.getPriority(); value= 档位的 下限和上限
            Map<String,Pair<Integer,Integer>> groupLevelPriorityLineMap = new HashMap<>();
            groupCodes.forEach(groupCode -> {
                String teacherCode = groupLeaderCodeMap.get(groupCode);
                String levelStr = teacherLevelMap.getOrDefault(teacherCode,"F");
                if (priorityConfigsGroupByLevelMap.containsKey(levelStr)){
                    List<LmTeacherLevelPriorityDTO> lmTeacherLevelPriorityDTOS = priorityConfigsGroupByLevelMap.get(levelStr);
                    int size = lmTeacherLevelPriorityDTOS.size();
                    for (int i=0;i<size;i++){
                        LmTeacherLevelPriorityDTO p = lmTeacherLevelPriorityDTOS.get(i);
                        if (levelStr.equals(p.getTeacherLevel())) {
                            String str = groupCode+"_"+p.getTeacherLevel()+"_"+p.getPriority();
                            if (i==size-1){
                                groupLevelPriorityLineMap.put(str,Pair.of(p.getLineIndex(),999999));
                            }else {
                                groupLevelPriorityLineMap.put(str,Pair.of(p.getLineIndex(),p.getNextLineIndex()));
                            }
                        }
                    }
                }
            });

            // 基础数据
            // 所有组的总库存
            int totalStock = groupStudentRemainNumMap.values().stream().mapToInt(s -> s).sum();
            int groupSize = groupCodes.size();
            int cardSize = Math.min(cardBaseInfoList.size(), totalStock);
            int[] groupArr = IntStream.range(0, groupSize).toArray();
            int[] cardCodeArr = IntStream.range(0, cardSize).toArray();
            int[] priorityArr = IntStream.range(0, priorityCount).toArray();
            int[] levelArr = IntStream.range(0, levelCount).toArray();
            // 系数限制
            Map<Integer,Integer> groupStockIndexMap = new HashMap<>();
            Map<Integer,Integer> groupMaxStuIndexMap = new HashMap<>();
            Map<Integer,String> groupIndexMap = new HashMap<>();
            Map<Integer,Integer> groupCurrentStuIndexMap = new HashMap<>();
            Map<Integer,Integer> groupLevelIndexMap = new HashMap<>();
            for (int i=0;i<groupSize;i++) {
                String groupCode = groupCodes.get(i);
                groupStockIndexMap.put(i, groupStudentRemainNumMap.getOrDefault(groupCode,0));
                groupMaxStuIndexMap.put(i, groupMaxStuNumMap.getOrDefault(groupCode,0));
                groupIndexMap.put(i, groupCode);
                groupCurrentStuIndexMap.put(i, groupCurrentStuNumMap.getOrDefault(groupCodes.get(i),0));
                groupLevelIndexMap.put(i,TeacherGroupLevelEnum.getByDesc(teacherLevelMap.getOrDefault(groupLeaderCodeMap.get(groupCode),"F")).getCode());
            }
            Map<Integer,LmMemberCardBaseInfoDTO> cardIndexMap = new HashMap<>();
            for (int i=0;i<cardSize;i++) {
                cardIndexMap.put(i, cardBaseInfoList.get(i));
            }
            // 优先级系数  越靠前 系数越高
            Map<Integer,Integer> priorityCoeffMap = new HashMap<>();
            int priorityCoeff = priorityCount;
            for (int i=0;i<priorityCount;i++) {
                priorityCoeffMap.put(i,priorityCoeff);
                priorityCoeff -- ;
            }
            // 组级别系数 越靠前 系数越高
            Map<Integer,Integer> levelCoeffMap = new HashMap<>();
            int levelCoeff = levelCount;
            for (int i=0;i<levelCount;i++) {
                //levelCoeffMap.put(i,levelCoeff);
                levelCoeffMap.put(i,1);
                levelCoeff -- ;
            }
            // 所有组的总最大容量
            int totalMaxStuNum = groupMaxStuNumMap.keySet().stream().filter(groupCodes::contains).mapToInt(groupMaxStuNumMap::get).sum();
            // 所有组的总在读人数
            int totalCurrentStuNum = groupCurrentStuNumMap.keySet().stream().filter(groupCodes::contains).mapToInt(groupCurrentStuNumMap::get).sum();

            // or-tools
            Loader.loadNativeLibraries();
            // 组建模型
            CpModel cpModel = new CpModel();
            // 所有点位
            Literal[][][][] cardGroupPoints = new Literal[cardSize][groupSize][priorityCount][levelCount];
            for (Integer card : cardCodeArr){
                for (Integer group : groupArr){
                    for (Integer priority : priorityArr){
                        for (Integer level : levelArr){
                            cardGroupPoints[card][group][priority][level] = cpModel.newBoolVar(card+","+group+","+priority+","+level);
                        }
                    }
                }
            }

            // 每张卡正好进入一个组 (每个组只有一个级别，每个级别只有几个优先级)
            for (Integer card : cardCodeArr){
                LinearExprBuilder builder1 = LinearExpr.newBuilder();
                for (Integer group : groupArr){
                    Integer thisGroupLevel = groupLevelIndexMap.get(group);
                    for (Integer level : levelArr){
                        if (thisGroupLevel.equals(level+1)){
                            List<Integer> priorityList = levelAndPriorityMap.get(TeacherGroupLevelEnum.getByCode(level + 1).getDesc());
                            for (Integer priority : priorityArr){
                                if (priorityList.contains(priority+1)){
                                    builder1.addTerm(cardGroupPoints[card][group][priority][level],1);
                                }
                            }
                        }
                    }
                }
                cpModel.addEquality(builder1,1);
            }

            // 根据优先级和老师级别设置系数
            for (Integer card : cardCodeArr){
                LinearExprBuilder builder2 = LinearExpr.newBuilder();
                for (Integer group : groupArr){
                    Integer thisGroupLevel = groupLevelIndexMap.get(group);
                    for (Integer level : levelArr){
                        if (thisGroupLevel.equals(level+1)){
                            Integer levelCo = levelCoeffMap.get(level);
                            List<Integer> priorityList = levelAndPriorityMap.get(TeacherGroupLevelEnum.getByCode(level + 1).getDesc());
                            for (Integer priority : priorityArr){
                                if (priorityList.contains(priority+1)){
                                    Integer priorityCo = priorityCoeffMap.get(priority);
                                    builder2.addTerm(cardGroupPoints[card][group][priority][level],(levelCo+priorityCo)*100);
                                }
                            }
                        }
                    }
                }
                maximize(cpModel,builder2);
            }

            // 组人数上限 根据库存设置
            for (Integer group : groupArr){
                LinearExprBuilder builder = LinearExpr.newBuilder();
                for (Integer card : cardCodeArr){
                    for (Integer level : levelArr){
                        for (Integer priority : priorityArr){
                            builder.addTerm(cardGroupPoints[card][group][priority][level],1);
                        }
                    }
                }
                cpModel.addLessOrEqual(builder,groupStockIndexMap.get(group));
            }

            // 优先级档位限制
            for (Integer group : groupArr){
                Integer currentStuNum = groupCurrentStuIndexMap.get(group);
                for (Integer level : levelArr){
                    for (Integer priority : priorityArr){
                        LinearExprBuilder builder = LinearExpr.newBuilder();
                        for (Integer card : cardCodeArr){
                            builder.add(cardGroupPoints[card][group][priority][level]);
                        }
                        String thiskey = groupIndexMap.get(group)+"_"+(TeacherGroupLevelEnum.getByCode(level+1).getDesc())+"_"+(priority+1);
                        if (groupLevelPriorityLineMap.containsKey(thiskey)){
                            Pair<Integer, Integer> pair = groupLevelPriorityLineMap.get(thiskey);
                            Integer left = pair.getLeft();
                            Integer right = pair.getRight();
                            if (currentStuNum<left){
                                cpModel.addLessOrEqual(builder,pair.getRight()-pair.getLeft());
                            }
                            if (currentStuNum>=left && currentStuNum<right){
                                cpModel.addLessOrEqual(builder,pair.getRight()-currentStuNum);
                            }
                            if (currentStuNum >= right){
                                cpModel.addLessOrEqual(builder,0);
                            }
                        }else {
                            cpModel.addLessOrEqual(builder,0);
                        }
                    }
                }
            }

            // 求解
            CpSolver solver = new CpSolver();
            CpSolverStatus status = solver.solve(cpModel);
            // 没有排上的会员卡
            List<LmMemberCardBaseInfoDTO> noGroupCardList = new ArrayList<>();
            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                for (int card : cardCodeArr){
                    LmMemberCardBaseInfoDTO lmMemberCardBaseInfoDTO = cardIndexMap.get(card);
                    String memberCode = lmMemberCardBaseInfoDTO.getMemberCode();
                    boolean flag = true;
                    for (int group : groupArr) {
                        String groupCode = groupIndexMap.get(group);
                        for (Integer priority : priorityArr){
                            for (Integer level : levelArr){
                                if (solver.booleanValue(cardGroupPoints[card][group][priority][level])) {
                                    // 记录入组的卡关系
                                    cardAndGroupResultMap.put(memberCode,groupCode);
                                    // 更新组的在读人数
                                    groupCurrentStuNumMap.put(groupCode, groupCurrentStuNumMap.getOrDefault(groupCode,0)+1);
                                    flag = false;
                                    logger.info("会员卡 {} 学习大组 {} 级别 {} 优先级 {}.%n", memberCode,groupCode,TeacherGroupLevelEnum.getByCode(level+1).getDesc(),priority+1);
                                }
                            }
                        }
                    }
                    if (flag)noGroupCardList.add(lmMemberCardBaseInfoDTO);
                }
            } else {
                logger.info("没有方案");
            }
            logger.info("没有分组的卡："+JSON.toJSONString(noGroupCardList));
        });
        return cardAndGroupResultMap;
    }

    /**
     * 进行 or-tools 的建模 和 求解
     * @param cardAndGroupResultMap
     * @param groupCurrentStuNumMap
     * @param cardBaseInfoDTOList
     * @param groupCodes
     * @param priority
     * @param currentPriorityGroupStock
     * @return
     */
    private List<LmMemberCardBaseInfoDTO> executeOrTools(ConcurrentHashMap<String, String> cardAndGroupResultMap,
                                                         Map<String, Integer> groupCurrentStuNumMap,
                                                         List<LmMemberCardBaseInfoDTO> cardBaseInfoDTOList,
                                                         List<String> groupCodes, Integer priority,
                                                         Map<String, Integer> currentPriorityGroupStock,
                                                         Map<String, Integer> currentPriorityGroupMaxStuCount) {

        if (currentPriorityGroupStock.isEmpty() ||
                CollectionUtils.isEmpty(cardBaseInfoDTOList) ||
                priority == null ||
                CollectionUtils.isEmpty(groupCodes)){
            return cardBaseInfoDTOList;
        }

        Loader.loadNativeLibraries();
        // 基础数据
        int groupSize = groupCodes.size();
        int cardSize = cardBaseInfoDTOList.size();
        int[] groupArr = IntStream.range(0, groupSize).toArray();
        int[] cardCodeArr = IntStream.range(0, cardSize).toArray();
        // 所有组的总库存
        int totalStock = currentPriorityGroupStock.values().stream().mapToInt(s -> s).sum();
        // 所有组的总在读人数
        int totalCurrentStuNum = groupCurrentStuNumMap.keySet().stream().filter(groupCodes::contains).mapToInt(groupCurrentStuNumMap::get).sum();
        // 所有组的总最大容量
        int totalMaxStuNum = currentPriorityGroupMaxStuCount.keySet().stream().filter(groupCodes::contains).mapToInt(currentPriorityGroupMaxStuCount::get).sum();
        // 系数限制
        Map<Integer,Integer> groupStockMap = new HashMap<>();
        Map<Integer,Integer> groupMaxStuMap = new HashMap<>();
        Map<Integer,Integer> groupCurrentStuMap = new HashMap<>();
        Map<Integer,String> groupIndexMap = new HashMap<>();
        for (int i=0;i<groupSize;i++) {
            groupStockMap.put(i, currentPriorityGroupStock.getOrDefault(groupCodes.get(i),0));
            groupMaxStuMap.put(i, currentPriorityGroupMaxStuCount.getOrDefault(groupCodes.get(i),0));
            groupCurrentStuMap.put(i, groupCurrentStuNumMap.getOrDefault(groupCodes.get(i),0));
            groupIndexMap.put(i, groupCodes.get(i));
        }
        Map<Integer,LmMemberCardBaseInfoDTO> cardIndexMap = new HashMap<>();
        for (int i=0;i<cardSize;i++) {
            cardIndexMap.put(i, cardBaseInfoDTOList.get(i));
        }
        // 组建模型
        CpModel cpModel = new CpModel();
        // 所有点位
        Literal[][] cardGroupPoints = new Literal[cardSize][groupSize];
        for (Integer card : cardCodeArr){
            for (Integer group : groupArr){
                cardGroupPoints[card][group] = cpModel.newBoolVar(card+","+group);
            }
        }
        // 每张卡最多只能进入一个组
        for (Integer card : cardCodeArr){
            List<Literal> list = new ArrayList<>();
            for (Integer group : groupArr){
                list.add(cardGroupPoints[card][group]);
            }
            cpModel.addAtMostOne(list);
        }
        // 每个组的组内最大人数是固定的 不能超出
        for (Integer group : groupArr){
            LinearExprBuilder builder = LinearExpr.newBuilder();
            for (Integer card : cardCodeArr){
                builder.add(cardGroupPoints[card][group]);
            }
            cpModel.addLessOrEqual(builder,groupStockMap.get(group));
        }
        // 让所有组的如组人数尽可能均匀 ，上限 = (在读总人数+待入组卡总数)/总库存 * 单组人数上线 - 单组中之前已有的卡数
        if (totalStock > cardSize){
            for (Integer group : groupArr){
                LinearExprBuilder builder = LinearExpr.newBuilder();
                for (Integer card : cardCodeArr){
                    builder.add(cardGroupPoints[card][group]);
                }
                int totalCard = totalCurrentStuNum + cardSize;
                int thisGroupMaxStuNum = groupMaxStuMap.get(group);
                int thisGroupCurrentStuNum = groupCurrentStuMap.get(group);
                double stuNum = ((double) totalCard * thisGroupMaxStuNum) / (double) totalMaxStuNum - (double) (thisGroupCurrentStuNum);
                int floor = (int)Math.floor(stuNum);
                int ceil = (int)Math.ceil(stuNum);
                cpModel.addLinearConstraint(builder,floor,ceil);
            }
        }
        // 求解
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(cpModel);
        // 没有排上的会员卡
        List<LmMemberCardBaseInfoDTO> noGroupCardList = new ArrayList<>();
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            logger.info("智能分组的结果：");
            for (int card : cardCodeArr){
                LmMemberCardBaseInfoDTO lmMemberCardBaseInfoDTO = cardIndexMap.get(card);
                String memberCode = lmMemberCardBaseInfoDTO.getMemberCode();
                boolean flag = true;
                for (int group : groupArr) {
                    String groupCode = groupIndexMap.get(group);
                    if (solver.booleanValue(cardGroupPoints[card][group])) {
                        // 记录入组的卡关系
                        cardAndGroupResultMap.put(memberCode,groupCode);
                        // 更新组的在读人数
                        groupCurrentStuNumMap.put(groupCode, groupCurrentStuNumMap.getOrDefault(groupCode,0)+1);
                        flag = false;
                        logger.info("会员卡 {} 学习大组 {}.%n", memberCode,groupCode);
                    }
                }
                if (flag)noGroupCardList.add(lmMemberCardBaseInfoDTO);
            }
        } else {
            logger.info("该优先级("+ priority +")的组没有没有方案");
            noGroupCardList.addAll(cardBaseInfoDTOList);
        }
        return noGroupCardList;
    }

    /**
     * 组的库存
     * @param lmLearnGroupPOListExceptZero
     * @param groupMaxStuNumMap
     * @param groupLeaderCodeMap
     * @param groupCurrentStuNumMap
     * @param teacherLevelMap
     * @param teacherLevelAndMaxLineMap
     * @return
     */
    private Map<String, Integer> groupStockCurrentPriority(List<LmLearnGroupPO> lmLearnGroupPOListExceptZero,
                                                           Map<String, Integer> groupMaxStuNumMap,
                                                           Map<String, String> groupLeaderCodeMap,
                                                           Map<String, Integer> groupCurrentStuNumMap,
                                                           Map<String, String> teacherLevelMap,
                                                           Map<String, Pair<Integer, Integer>> teacherLevelAndMaxLineMap,
                                                           Map<String, Integer> currentPriorityGroupMaxStuCount,
                                                           Map<String,Integer> currentPriorityGroupStock,
                                                           List<String> groupCodes) {
        lmLearnGroupPOListExceptZero.forEach(groupPo -> {
            String groupCode = groupPo.getGroupCode();
            Integer maxStuNum = groupMaxStuNumMap.getOrDefault(groupCode, 0);
            String teacherCode = groupLeaderCodeMap.getOrDefault(groupCode,"");
            String teacherLevel = teacherLevelMap.getOrDefault(teacherCode,"F");
            Integer currentStuNum = groupCurrentStuNumMap.getOrDefault(groupCode, 0);
            Pair<Integer, Integer> linePair = teacherLevelAndMaxLineMap.get(teacherLevel);
            if (linePair!=null){
                int left = linePair.getLeft();
                int right = Math.min(maxStuNum,linePair.getRight());
                if (currentStuNum>=left && currentStuNum<right){
                    currentPriorityGroupStock.put(groupCode,Math.max(right-currentStuNum,0));
                    currentPriorityGroupMaxStuCount.put(groupCode,right);
                    groupCodes.add(groupCode);
                }
            }
        });
        return currentPriorityGroupStock;
    }

    /**
     * 该优先级下，老师可以带的最大带生量
     * @param priorityConfigsGroupByMap
     * @param priority
     * @return
     */
    private Map<String, Pair<Integer, Integer>> teacherMaxStuNumCurrentPriority(Map<Integer, List<LmTeacherLevelPriorityDTO>> priorityConfigsGroupByMap, Integer priority) {
        Map<String,Pair<Integer,Integer>> teacherLevelAndMaxLineMap = new HashMap<>();
        List<LmTeacherLevelPriorityDTO> lmTeacherLevelPriorityDTOS = priorityConfigsGroupByMap.get(priority);
        Map<String,List<LmTeacherLevelPriorityDTO>> map = lmTeacherLevelPriorityDTOS.stream().collect(Collectors.groupingBy(LmTeacherLevelPriorityDTO::getTeacherLevel));
        map.forEach((teacherLevel,v)->{
            v.forEach(dto -> {
                teacherLevelAndMaxLineMap.put(teacherLevel,Pair.of(dto.getLineIndex(),dto.getNextLineIndex()));
            });
        });
        return teacherLevelAndMaxLineMap;
    }


    /**
     * or-tools 最大目标
     */
    private static void maximize(CpModel model, LinearArgument builder) {
        CpObjectiveProto.Builder obj = model.getBuilder().getObjectiveBuilder();
        LinearExpr e = builder.build();
        for(int i = 0; i < e.numElements(); ++i) {
            obj.addVars(e.getVariableIndex(i)).addCoeffs(-e.getCoefficient(i));
        }
        obj.setOffset((double)(-e.getOffset()));
        obj.setScalingFactor(-1.0D);
    }
    public static void minimize(CpModel model, LinearArgument expr) {
        CpObjectiveProto.Builder obj = model.getBuilder().getObjectiveBuilder();
        LinearExpr e = expr.build();

        for(int i = 0; i < e.numElements(); ++i) {
            obj.addVars(e.getVariableIndex(i)).addCoeffs(e.getCoefficient(i));
        }

        obj.setOffset((double)e.getOffset());
    }


    /**
     * 计算库存
     * @param schoolId
     * @param cardStartTime
     * @param cardEndTime
     * @param divideType
     * @param effectDate
     * @param lmLearnGroupPOList
     * @return
     */
    private Map<String, Integer> getGroupStockMap(Integer schoolId,String cardStartTime,String cardEndTime,
                                                  Integer divideType, String effectDate, List<LmLearnGroupPO> lmLearnGroupPOList) {
        List<String> allGroupCodeList = lmLearnGroupPOList.stream().map(LmLearnGroupPO::getGroupCode).collect(Collectors.toList());
        Map<String,Integer> groupStudentRemainNumMap = Maps.newHashMap();
        if(Objects.equals(divideType, AutoDivideGroupTypeEnum.COMMON.getStatus())||Objects.equals(divideType, AutoDivideGroupTypeEnum.TRANSFER.getStatus())){
            groupStudentRemainNumMap.putAll(lmLearnGroupStudentManager.queryGroupMinSurplusCapacityMap(schoolId,
                    allGroupCodeList, Pair.of(cardStartTime, cardEndTime)));
        }else if(Objects.equals(divideType, AutoDivideGroupTypeEnum.GRADE_UP.getStatus())){
            groupStudentRemainNumMap.putAll(lmLearnGroupManager.queryLearnGroupStudentRemainNum(schoolId,allGroupCodeList, effectDate));
        }
        return groupStudentRemainNumMap;
    }

    /**
     * 根据会员卡的信息判断有没有对应的组，给出提示【未建组】
     */
    private void checkCardNoExistGroup(Integer schoolId, AutoDivideFailResultDto autoDivideFailResultDto,
                                       Map<String, List<LmMemberCardBaseInfoDTO>> teachingInfoCardMap,
                                       List<LmLearnGroupPO> lmLearnGroupPOList) {
        // 将组根据 产品、科目、年级、版本、层级 分组
        Map<String,List<LmLearnGroupPO>> teachingGroupInfoMap = lmLearnGroupPOList.stream().collect(Collectors.groupingBy(FUN_LEARN_GROUP_SELECT_PO));
        StringBuffer errorMsg = autoDivideFailResultDto.getErrorMsg();
        ConcurrentHashMap<String, LmMemberCardBaseInfoDTO> nonExistentGroupCardMap = autoDivideFailResultDto.getNonExistentGroupCardMap();
        teachingInfoCardMap.forEach((key, cardBaseInfoDTOList)->{
            //没有对应的组信息
            if(!teachingGroupInfoMap.containsKey(key)){
                cardBaseInfoDTOList.forEach(x-> nonExistentGroupCardMap.put(x.getMemberCode(),x));
                List<String> teachingInfoList = Splitter.on(",").splitToList(key);
                errorMsg.append(String.format(AutoDivideGroupErrorEnum.A.getDesc(), schoolId,teachingInfoList.get(0)
                        ,teachingInfoList.get(1),teachingInfoList.get(2),teachingInfoList.get(3),teachingInfoList.get(4)));
            }
        });
    }

    /**
     * 获取可用的组
     * @param paramList
     * @param schoolId
     * @return
     */
    private List<LmLearnGroupPO> getLmLearnGroupPOS(List<LmMemberCardBaseInfoDTO> paramList, Integer schoolId) {
        List<String> productCodeList  = paramList.stream().map(LmMemberCardBaseInfoDTO::getProductId).distinct().collect(Collectors.toList());
        List<String> subjectCodeList  = paramList.stream().map(LmMemberCardBaseInfoDTO::getSubjectCode).distinct().collect(Collectors.toList());
        List<String> gradeCodeList  = paramList.stream().map(LmMemberCardBaseInfoDTO::getStudyGradeCode).distinct().collect(Collectors.toList());
        List<String> bookCodeList  = paramList.stream().map(LmMemberCardBaseInfoDTO::getBookCode).distinct().collect(Collectors.toList());
        List<String> difficultyLevelCodeList  = paramList.stream().map(LmMemberCardBaseInfoDTO::getDifficultyLevelCode).distinct().collect(Collectors.toList());
        LmGroupTeachingParam lmGroupTeachingParam = new LmGroupTeachingParam();
        lmGroupTeachingParam.setSchoolId(schoolId);
        lmGroupTeachingParam.setProductCodeList(productCodeList);
        lmGroupTeachingParam.setSubjectCodeList(subjectCodeList);
        lmGroupTeachingParam.setGradeCodeList(gradeCodeList);
        lmGroupTeachingParam.setBookCodeList(bookCodeList);
        lmGroupTeachingParam.setDifficultyLevelCodeList(difficultyLevelCodeList);
        return Optional.ofNullable(lmLearnGroupManager.queryAutoDivideLearnGroupList(lmGroupTeachingParam)).orElse(new ArrayList<>(1));
    }

}
