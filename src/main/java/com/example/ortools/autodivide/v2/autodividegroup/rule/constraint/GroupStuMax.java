package com.example.ortools.autodivide.v2.autodividegroup.rule.constraint;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.annotation.EasyRulesAnnotation;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import org.jeasy.rules.annotation.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Rule(priority = 1)
@EasyRulesAnnotation(name = "groupStuMax",description = "同条件下的组人数有限制",ruleType = EasyRuleEnum.AUTO_DIVIDE_GROUP)
@Service
public class GroupStuMax extends BaseRule {

    @Condition
    public boolean condition(@Fact("autoDivideOrToolsDto") AutoDivideOrToolsDto autoDivideOrToolsDto){
        super.init(autoDivideOrToolsDto);

        return Boolean.TRUE;
    }

    @Action
    public void action(@Fact("cpModel")CpModel cpModel){

        // 组人数上限 根据库存设置
        productKeyAndCardCountMap.forEach((productKey,cardMaxCount) -> {
            List<Integer> groupIndexList = productKeyAndGroupIndexMap.getOrDefault(productKey,new ArrayList<>(1));
            LinearExprBuilder builder = LinearExpr.newBuilder();
            for (Integer group : groupIndexList){
                for (Integer index : indexArr){
                    List<Integer> priorityList = getGroupIndexAndIndexAndPriorityIndexMap().getOrDefault(group+"_"+index, new ArrayList<>(1));
                    for (Integer priority : priorityList){
                        builder.add(cardGroupPoints[group][priority][index]);
                    }
                }
            }
            cpModel.addLessOrEqual(builder,cardMaxCount);
        });
    }

}
