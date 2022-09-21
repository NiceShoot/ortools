package com.example.ortools.autodivide.v2.autodividegroup.listener;

import cn.xdf.api.resource.management.bean.dto.CommonDto;
import cn.xdf.api.resource.management.bean.dto.learningmachine.AutoDivideFailResultDto;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmGroupTeachingDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmTeacherLevelPriorityDTO;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterGradeNumberV2DTO;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterLearnMachineGdProductDTO;
import cn.xdf.api.resource.management.bean.dto.third.CourseCenterSubjectDTO;
import cn.xdf.api.resource.management.bean.param.LmGroupTeachingParam;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmConfigLevelRootPO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmDivideGroupFailRecordPO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import cn.xdf.api.resource.management.bean.po.school.SchoolConfigPO;
import cn.xdf.api.resource.management.config.apollo.CloudOfficeConfig;
import cn.xdf.api.resource.management.enums.AutoDivideGroupErrorEnum;
import cn.xdf.api.resource.management.enums.AutoDivideGroupFailReasonEnum;
import cn.xdf.api.resource.management.enums.AutoDivideGroupTypeEnum;
import cn.xdf.api.resource.management.enums.TeacherGroupLevelEnum;
import cn.xdf.api.resource.management.exception.ApiException;
import cn.xdf.api.resource.management.exception.ApiExceptionMetas;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideParam;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideResultDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import cn.xdf.api.resource.management.http.ICourseCenterHttpClient;
import cn.xdf.api.resource.management.manager.*;
import cn.xdf.api.resource.management.service.ICloudOfficeService;
import cn.xdf.api.resource.management.service.ILmConfigLevelPriorityService;
import cn.xdf.api.resource.management.service.ILmConfigLevelRootService;
import cn.xdf.api.resource.management.threadpool.XdfThreadPoolExecutor;
import com.alibaba.fastjson.JSON;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngineListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
public class AutoDivideGroupRuleEngineListener implements RulesEngineListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoDivideGroupRuleEngineListener.class);

    private ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    @Autowired
    private ILmLearnGroupManager lmLearnGroupManager;
    @Autowired
    private ILmLearnGroupStudentManager lmLearnGroupStudentManager;
    @Autowired
    private ILmConfigLevelRootService lmConfigLevelRootService;
    @Autowired
    private ILmConfigLevelPriorityService lmConfigLevelPriorityService;
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

    @Override
    public void beforeEvaluate(Rules rules, Facts facts) {
        threadLocal.set(System.currentTimeMillis());
        AutoDivideParam autoDivideParam = facts.get("autoDivideParam");
        AutoDivideDto autoDivideDto = facts.get("autoDivideDto");
        // 初始化错误信息实体
        AutoDivideFailResultDto autoDivideFailResultDto = new AutoDivideFailResultDto();
        if (autoDivideDto == null){
            // 初始化智能分组入参实体
            autoDivideDto = new AutoDivideDto();
            // 固定参数
            Integer schoolId = autoDivideParam.getSchoolId();
            String managementDeptCode = autoDivideParam.getManagementDeptCode();
            // 将会员卡入参 根据 产品、科目、年级、版本、层级 分组
            autoDivideDto.setTeachingInfoCardMap(autoDivideParam.getCardBaseInfoDTOListParam().stream().collect(Collectors.groupingBy(BaseRule.FUN_LEARN_GROUP_SELECT_PARAM)));
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
        }
        // ----------------------------or-tools
        Loader.loadNativeLibraries();
        // 组建模型
        CpModel cpModel = new CpModel();
        // 参数转换
        AutoDivideOrToolsDto autoDivideOrToolsDto = new AutoDivideOrToolsDto(autoDivideDto,cpModel);
        // 加入上下文
        facts.put("autoDivideFailResultDto",autoDivideFailResultDto);
        facts.put("autoDivideOrToolsDto",autoDivideOrToolsDto);
        facts.put("cpModel",cpModel);

    }



    @Override
    public void afterExecute(Rules rules, Facts facts) {
        try {
            AutoDivideFailResultDto autoDivideFailResultDto = facts.get("autoDivideFailResultDto");
            AutoDivideParam autoDivideParam = facts.get("autoDivideParam");
            AutoDivideResultDto autoDivideResultDto = facts.get("autoDivideResultDto");
            this.orToolsSolve(facts);
            //this.failHandleFun(autoDivideParam,autoDivideFailResultDto,autoDivideResultDto.getCardAndGroupResultMap());
        }catch (Exception e){
            logger.error("or-tolls自动分组异常",e);
            throw new ApiException(ApiExceptionMetas.SYS_ERROR.getStatus(),"自动分组保存失败记录失败");
        }finally {
            logger.info("or-tools执行整体耗时:{}", System.currentTimeMillis() - threadLocal.get());
            threadLocal.remove();
        }
    }


    /**
     * 失败数据处理
     */
    private void failHandleFun(AutoDivideParam autoDivideParam, AutoDivideFailResultDto autoDivideFailResultDto, ConcurrentHashMap<String, String> cardAndGroupResultMap) {
        Integer schoolId = autoDivideParam.getSchoolId();
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
                        .collect(Collectors.groupingBy(BaseRule.FUN_LEARN_GROUP_SELECT_PO));
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
     * 结果解析
     */
    public void orToolsSolve(Facts facts) {
        AutoDivideOrToolsDto autoDivideOrToolsDto = facts.get("autoDivideOrToolsDto");
        CpModel cpModel = facts.get("cpModel");
        IntVar[][][] cardGroupPoints = autoDivideOrToolsDto.getCardGroupPoints();
        Map<String, Long> cardAndGroupResultMap = new HashMap<>();
        Map<String, String> cardAndGroupResultMapDesc = new HashMap<>();

        // 求解
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(cpModel);
        // 没有排上的会员卡
        List<LmMemberCardBaseInfoDTO> noGroupCardList = new ArrayList<>();
        int key = 0;
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (int group : autoDivideOrToolsDto.getGroupArr()) {
                LmLearnGroupPO lmLearnGroupPO = autoDivideOrToolsDto.getLmLearnGroupPOS().get(group);
                for (Integer priority : autoDivideOrToolsDto.getPriorityArr()){
                    for (Integer index : autoDivideOrToolsDto.getIndexArr()){
                        long value = solver.value(cardGroupPoints[group][priority][index]);
                        if (value!=0) {
                            // 记录入组的卡关系
                            Long count = cardAndGroupResultMap.getOrDefault(lmLearnGroupPO.getGroupCode(), 0L);
                            cardAndGroupResultMap.put(lmLearnGroupPO.getGroupCode(),count+value);
                            String format = String.format("学习大组：%s； 档位：%s； 优先级：%s；人数：%s", lmLearnGroupPO.getGroupCode(), index, autoDivideOrToolsDto.getPriorities().get(priority), value);
                            logger.info("学习大组 {} 档位 {} 优先级 {} 人数 {}.%n",
                                    lmLearnGroupPO.getGroupCode(),index,autoDivideOrToolsDto.getPriorities().get(priority),value);
                            cardAndGroupResultMapDesc.put(key+"",format);
                            key++;
                        }
                    }
                }
            }
        } else {
            logger.info("没有方案");
        }
        logger.info("没有分组的卡："+JSON.toJSONString(noGroupCardList));
        autoDivideOrToolsDto.setCardAndGroupResultMap(cardAndGroupResultMap);
        autoDivideOrToolsDto.setCardAndGroupResultMapDesc(cardAndGroupResultMapDesc);
    }

    /**
     * 计算库存
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
        Map<String,List<LmLearnGroupPO>> teachingGroupInfoMap = lmLearnGroupPOList.stream().collect(Collectors.groupingBy(BaseRule.FUN_LEARN_GROUP_SELECT_PO));
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
