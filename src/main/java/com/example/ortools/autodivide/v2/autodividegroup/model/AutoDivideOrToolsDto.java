package com.example.ortools.autodivide.v2.autodividegroup.model;

import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.bean.dto.learningmachine.LmTeacherLevelPriorityDTO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.primitives.Ints.asList;

@Data
public class AutoDivideOrToolsDto extends BaseOrToolsModel  {


    // 结果
    Map<String, Long> cardAndGroupResultMap;
    Map<String, String> cardAndGroupResultMapDesc;

    /**
     * ----------------------------入参
     */
    // 会员卡列表
    List<LmMemberCardBaseInfoDTO> cardBaseInfoList;
    // 学习大组列表
    List<LmLearnGroupPO> lmLearnGroupPOS;
    // 学习大组上限人数Map，key=组编码，value=上限人数
    Map<String, Integer> groupMaxStuNumMap;
    // key=学习组编码，value = 组长编码
    Map<String, String> groupLeaderCodeMap;
    // key=组编码，value=在读人数（0,1）
    Map<String, Integer> groupCurrentStuNumMap;
    // key=老师编码，value=级别（A，B，C，D，E，F）
    Map<String, String> teacherLevelMap;
    // key=组编码，value=库存
    Map<String, Integer> groupStudentRemainNumMap;
    // 优先级配置列表
    List<LmTeacherLevelPriorityDTO> priorityConfigs;
    // 产品,科目,年级,教材,层级 拼接成的key 集合
    List<String> productKeyList;
    // key=产品，科目，年级，版本，层级 ; value = 组
    Map<String, List<LmLearnGroupPO>> teachingGroupMap;
    // key=index ; value = 优先级
    Map<Integer, List<Integer>> indexAndPriorityIndexMap = new HashMap<>();
    /**
     * ----------------------------入参转换
     */
    // 学习大组编码列表
    List<String> groupCodesList;
    // 优先级列表
    List<Integer> priorities;
    // 档位列表
    List<Integer> indexes;
    // key=老师级别，value=优先级码列表
    Map<String, List<Integer>> levelAndPriorityMap;
    Map<Integer,List<Integer>> groupIndexAndPriorityIndexMap = new HashMap<>();

    public AutoDivideOrToolsDto(AutoDivideDto autoDivideDto, CpModel cpModel) {

        // ----------------------------入参
        this.cardBaseInfoList = autoDivideDto.getCardBaseInfoDTOS();
        this.lmLearnGroupPOS = autoDivideDto.getLmLearnGroupPOListExceptZero();
        this.groupMaxStuNumMap = autoDivideDto.getGroupMaxStuNumMap();
        this.groupLeaderCodeMap = autoDivideDto.getGroupLeaderCodeMap();
        this.groupCurrentStuNumMap = autoDivideDto.getGroupCurrentStuNumMap();
        this.teacherLevelMap = autoDivideDto.getTeacherLevelMap();
        this.groupStudentRemainNumMap = autoDivideDto.getGroupStudentRemainNumMap();
        this.priorityConfigs = autoDivideDto.getPriorityConfigs();
        this.productKeyList = new ArrayList<>(autoDivideDto.getTeachingInfoCardMap().keySet());
        this.teachingGroupMap = autoDivideDto.getTeachingGroupMap();

        // ----------------------------入参转换
        this.groupCodesList = lmLearnGroupPOS.stream().map(LmLearnGroupPO::getGroupCode).distinct().collect(Collectors.toList());
        this.priorities = priorityConfigs.stream().map(LmTeacherLevelPriorityDTO::getPriority).distinct().sorted().collect(Collectors.toList());
        this.indexes = priorityConfigs.stream().map(LmTeacherLevelPriorityDTO::getIndexA).distinct().collect(Collectors.toList());
        this.lineIndexMap = priorityConfigs.stream().collect(Collectors.toMap(LmTeacherLevelPriorityDTO::getIndexA, s -> Pair.of(s.getLineIndex(), s.getNextLineIndex()), (s1, s2) -> s1));
        this.levelAndPriorityMap = priorityConfigs.stream().collect(Collectors.groupingBy(LmTeacherLevelPriorityDTO::getTeacherLevel, Collectors.mapping(LmTeacherLevelPriorityDTO::getPriority, Collectors.toList())));

        // key=产品,科目,年级,教材,层级 拼接成的key, value=会员卡数量
        Map<String, List<LmMemberCardBaseInfoDTO>> teachingInfoCardMap = autoDivideDto.getTeachingInfoCardMap();
        teachingInfoCardMap.forEach((k,v)-> productKeyAndCardCountMap.put(k,v.size()));

        // ----------------------------初始化数组
        this.groupArr = IntStream.range(0, lmLearnGroupPOS.size()).toArray();
        this.priorityArr = IntStream.range(0, priorities.size()).toArray();
        this.indexArr = IntStream.range(0, indexes.size()).toArray();

        Map<String,Integer> groupCodeAndGroupIndexMap = new HashMap<>();
        for (int i = 0; i < lmLearnGroupPOS.size(); i++) {
            groupCodeAndGroupIndexMap.put(lmLearnGroupPOS.get(i).getGroupCode(),i);
        }
        Map<Integer,Integer> priorityAndPriorityIndexMap = new HashMap<>();
        for (int i = 0; i < priorities.size(); i++) {
            priorityAndPriorityIndexMap.put(priorities.get(i),i);
        }
        List<Integer> indexList = asList(indexArr);

        // ----------------------------数组角标与实际值对应关系
        for (int i=0;i<groupArr.length;i++) {
            LmLearnGroupPO lmLearnGroupPO = lmLearnGroupPOS.get(i);
            String groupCode = lmLearnGroupPO.getGroupCode();
            // key=组角标 value=库存
            this.groupIndexStockMap.put(i, groupStudentRemainNumMap.getOrDefault(groupCode,0));
            // key=组角标 value=在读人数
            this.groupIndexCurrentStuMap.put(i, groupCurrentStuNumMap.getOrDefault(groupCode,0));
            String productKey = BaseRule.FUN_LEARN_GROUP_SELECT_PO.apply(lmLearnGroupPO);
            // key=productKey value=组角标
            List<Integer> groupIndexes = this.productKeyAndGroupIndexMap.get(productKey);
            if (groupIndexes == null){
                groupIndexes = new ArrayList<>();
                groupIndexes.add(i);
                this.productKeyAndGroupIndexMap.put(productKey,groupIndexes);
            }else {
                groupIndexes.add(i);
            }
            // 组角标对应的优先级角标
            String teacherCode = groupLeaderCodeMap.get(groupCode);
            String levelStr = teacherLevelMap.getOrDefault(teacherCode,"F");
            groupIndexAndPriorityIndexMap.put(i,levelAndPriorityMap.getOrDefault(levelStr,new ArrayList<>(1)).stream().map(priorityAndPriorityIndexMap::get).collect(Collectors.toList()));
            // 组角标对应的档位index角标
            groupIndexAndIndexIndexMap.put(i, indexList);
        }
        // 优先级角标下的组角标
        groupIndexAndPriorityIndexMap.forEach((k,v)->{
            v.forEach(priority -> {
                List<Integer> groupIndexes = priorityIndexAndGroupIndexMap.get(priority);
                if (groupIndexes == null){
                    groupIndexes = new ArrayList<>();
                    groupIndexes.add(k);
                    priorityIndexAndGroupIndexMap.put(priority,groupIndexes);
                }else {
                    groupIndexes.add(k);
                }
            });
        });

        // 档位和优先级的关系，角标
        priorityConfigs.forEach(s->{
            Integer indexA = s.getIndexA();
            List<Integer> integers = indexAndPriorityIndexMap.get(indexA);
            if (integers == null){
                integers = new ArrayList<>();
                integers.add(priorityAndPriorityIndexMap.get(s.getPriority()));
                indexAndPriorityIndexMap.put(indexA,integers);
            }else {
                integers.add(priorityAndPriorityIndexMap.get(s.getPriority()));
            }
        });
        indexAndPriorityIndexMap.forEach((index,indexPriorityList)->{
            groupIndexAndPriorityIndexMap.forEach((groupIndex,priorityList)->{
                groupIndexAndIndexAndPriorityIndexMap.put(groupIndex+"_"+index,indexPriorityList.stream().filter(priorityList::contains).collect(Collectors.toList()));
            });
        });

        // 根据优先级设置系数
        int priorityCoeff = priorityArr.length;
        for (int i=0;i<priorityArr.length;i++) {
            priorityCoeffMap.put(i,priorityCoeff);
            priorityCoeff -- ;
        }

        // 所有点位
        IntVar[][][] cardGroupPoints =
                new IntVar[groupArr.length][priorityArr.length][indexArr.length];
        for (Integer group : groupArr){
            Integer currentStuNum = groupIndexCurrentStuMap.get(group);
            for (Integer index : indexArr){
                List<Integer> thisGroupAndIndexPriorityList = groupIndexAndIndexAndPriorityIndexMap.get(group + "_" + index);
                for (Integer priority : priorityArr){
                    if (thisGroupAndIndexPriorityList.contains(priority)){
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
                        cardGroupPoints[group][priority][index] = cpModel.newIntVar(0,temp,"每个点放的人数");
                    }else {
                        cardGroupPoints[group][priority][index] = cpModel.newIntVar(0,0,"每个点放的人数");
                    }
                }
            }
        }
        this.cardGroupPoints = cardGroupPoints;
    }
}
