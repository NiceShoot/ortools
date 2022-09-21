package com.example.ortools.自动分组.v2.autodividegroup.rule.optimal;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.annotation.EasyRulesAnnotation;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.utils.OrToolsUtils;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import org.jeasy.rules.annotation.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Rule(priority = 1)
@EasyRulesAnnotation(name = "groupPriorityMax",description = "优先级目标，尽量在优先级高的组分人",ruleType = EasyRuleEnum.AUTO_DIVIDE_GROUP)
@Service
public class GroupPriorityMax extends BaseRule {

    @Condition
    public boolean condition(@Fact("autoDivideOrToolsDto") AutoDivideOrToolsDto autoDivideOrToolsDto){
        super.init(autoDivideOrToolsDto);

        return Boolean.TRUE;
    }

    @Action
    public void action(@Fact("cpModel")CpModel cpModel){

        // 根据优先级和老师级别设置系数
        LinearExprBuilder builder = LinearExpr.newBuilder();
        for (Integer group : groupArr){
            for (Integer index : indexArr){
                List<Integer> priorityList = groupIndexAndIndexAndPriorityIndexMap.getOrDefault(group+"_"+index,new ArrayList<>(1));
                for (Integer priority : priorityList){
                    Integer priorityCo = priorityCoeffMap.get(priority);
                    builder.addTerm(cardGroupPoints[group][priority][index],(priorityCo)*10000);
                }
            }
        }
        OrToolsUtils.maximize(cpModel,builder);
    }
}
