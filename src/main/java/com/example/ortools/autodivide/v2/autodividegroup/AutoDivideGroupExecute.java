package com.example.ortools.autodivide.v2.autodividegroup;

import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.dingding.DingDingService;
import cn.xdf.api.resource.management.exception.ApiException;
import cn.xdf.api.resource.management.exception.ApiExceptionMetas;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideParam;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.utils.AutoDivideGroupRuleUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class AutoDivideGroupExecute implements ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(AutoDivideGroupExecute.class);

    private ApplicationContext applicationContext;

    @Resource(name = "autoDivideGroupRuleEngine")
    private RulesEngine autoDivideGroupRuleEngine;
    @Autowired
    private DingDingService dingDingService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, Long> execute(AutoDivideParam autoDivideParam) {
        // check入参
        if (!this.checkParam(autoDivideParam)) throw new ApiException(ApiExceptionMetas.SYS_ERROR.getStatus(),"自动分组失败");

        Facts facts = new Facts();
        facts.put("autoDivideParam",autoDivideParam);
        // 注册规则
        Rules rules = AutoDivideGroupRuleUtil.registerRules(EasyRuleEnum.AUTO_DIVIDE_GROUP, applicationContext);
        autoDivideGroupRuleEngine.fire(rules,facts);
        AutoDivideOrToolsDto autoDivideOrToolsDto = facts.get("autoDivideOrToolsDto");
        return autoDivideOrToolsDto.getCardAndGroupResultMap();
    }

    public Map<String, String> executeTest(AutoDivideDto autoDivideDto) {

        Facts facts = new Facts();
        facts.put("autoDivideDto",autoDivideDto);
        // 注册规则
        Rules rules = AutoDivideGroupRuleUtil.registerRules(EasyRuleEnum.AUTO_DIVIDE_GROUP, applicationContext);
        autoDivideGroupRuleEngine.fire(rules,facts);
        AutoDivideOrToolsDto autoDivideOrToolsDto = facts.get("autoDivideOrToolsDto");
        return autoDivideOrToolsDto.getCardAndGroupResultMapDesc();
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


}
