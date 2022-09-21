package com.example.ortools.自动分组.v2.autodividegroup.rule.constraint;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.annotation.EasyRulesAnnotation;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import org.apache.commons.lang3.tuple.Pair;
import org.jeasy.rules.annotation.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Rule(priority = 1)
@EasyRulesAnnotation(name = "groupPriorityJump",description = "每个组必须前面一个档位人满之后再进入后面的档位",ruleType = EasyRuleEnum.AUTO_DIVIDE_GROUP)
@Service
public class GroupPriorityJump extends BaseRule {

    @Condition
    public boolean condition(@Fact("autoDivideOrToolsDto") AutoDivideOrToolsDto autoDivideOrToolsDto){
        super.init(autoDivideOrToolsDto);
        return Boolean.TRUE;
    }

    @Action
    public void action( @Fact("cpModel")CpModel cpModel){

        for (Integer group : groupArr){
            Integer currentStuNum = groupIndexCurrentStuMap.get(group);
            for (int index=0;index<indexArr.length-1;index++){

                LinearExprBuilder builder1 = LinearExpr.newBuilder();
                List<Integer> priorityList1 = groupIndexAndIndexAndPriorityIndexMap.get(group + "_" + index);
                for (Integer priority : priorityList1){
                    builder1.add(cardGroupPoints[group][priority][index]);
                }

                LinearExprBuilder builder2 = LinearExpr.newBuilder();
                List<Integer> priorityList2 = groupIndexAndIndexAndPriorityIndexMap.get(group + "_" + (index+1));
                for (Integer priority : priorityList2){
                    builder2.add(cardGroupPoints[group][priority][index+1]);
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
                Literal literal = cpModel.newBoolVar( "");
                cpModel.addEquality(builder1,temp).onlyEnforceIf(literal);
                cpModel.addGreaterOrEqual(builder2,0).onlyEnforceIf(literal);
                cpModel.addLessOrEqual(builder1,temp).onlyEnforceIf(literal.not());
                cpModel.addEquality(builder2,0).onlyEnforceIf(literal.not());
            }
        }

    }


}
