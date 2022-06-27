package com.example.ortools.start.cp;

import com.google.common.collect.Lists;
import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Cp学生大班排课v3 {

    public static void main(String[] args) {

        /**
         * 模拟学生排课
         *      时段：7天（周一到周日，周六日不上课），每天4个时段 共28个时段
         *      学生：1个
         *      频次：语文5，数学3，英语5，体育2，自习5
         *      每个时段只能上一科
         *      每科每天只能上一次
         *      自习课都在每天的最后一节课
         *      尽量均匀分布
         */
        Loader.loadNativeLibraries();
        int dayNum=7,timeNum=4,studentNum=1,subjectNum = 2;
        // 基础数据
        int[] days = IntStream.range(0, dayNum).toArray();
        int[] times = IntStream.range(0, timeNum).toArray();
        int[] students = IntStream.range(0, studentNum).toArray();
        int[] subjects = IntStream.range(0, subjectNum).toArray();
        // 周课频
        Map<Integer,Integer> frequency = new HashMap<>();
        frequency.put(0,14);frequency.put(1,14);frequency.put(2,5);frequency.put(3,2);frequency.put(4,5);
        // 科目名称
        Map<Integer,String> subNameMap = new HashMap<>();
        subNameMap.put(0,"语文");subNameMap.put(1,"数学");subNameMap.put(2,"英语");subNameMap.put(3,"体育");subNameMap.put(4,"自习");
        // 周次名称
        Map<Integer,String> weekNameMap = new HashMap<>();
        weekNameMap.put(0,"周一");weekNameMap.put(1,"周二");weekNameMap.put(2,"周三");weekNameMap.put(3,"周四");weekNameMap.put(4,"周五");weekNameMap.put(5,"周六");weekNameMap.put(6,"周日");
        // 学生不可用时间
        // 老师不可用时间
        // 国家规定周六日不能上课,[day][time]
        int[][] countryAvailableTime  = new int[][]{
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1}
        };


        // 组建模型
        CpModel model = new CpModel();
        // 所有点位
        Literal[][][][] lessonPoints = new Literal[studentNum][subjectNum][dayNum][timeNum];
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        lessonPoints[stu][sub][day][time] = model.newBoolVar(stu+","+sub+","+day+","+time);
                    }
                }
            }
        }
        // 学生可排时段
        LinearExprBuilder builder2 = LinearExpr.newBuilder();
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        if (countryAvailableTime[day][time]  == 1){
                            builder2.addTerm(lessonPoints[stu][sub][day][time],1);
                        }else {
                            builder2.addTerm(lessonPoints[stu][sub][day][time],0);
                        }
                    }
                }
            }
        }
        model.maximize(builder2);

        // 每个时段只能上一科
        for (Integer stu : students){
            for (Integer day : days){
                for (Integer time : times){
                    List<Literal> list = new ArrayList<>();
                    for (Integer sub : subjects){
                        list.add(lessonPoints[stu][sub][day][time]);
                    }
                    model.addAtMostOne(list);
                }
            }
        }

        // 让均匀散列
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        if (time<times.length-1){
                            List<Literal> list = new ArrayList<>();
                            list.add(lessonPoints[stu][sub][day][time]);
                            list.add(lessonPoints[stu][sub][day][time+1]);
                            model.addBoolOr(list);
                        }
                    }
                }
            }
        }

        // 设置每一科的课频
        for (Integer stu : students){
            for (Integer sub : subjects){
                LinearExprBuilder builder = LinearExpr.newBuilder();
                for (Integer day : days){
                    for (Integer time : times){
                        builder.add(lessonPoints[stu][sub][day][time]);
                    }
                }
                model.addLessOrEqual(builder,frequency.get(sub));
            }
        }

        // 求解
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Solution:%n");
            for (int stu : students){
                System.out.printf("学生 %d%n",stu);

                StringBuilder dayBuilder = new StringBuilder("   ");
                for (int day : days) {
                    dayBuilder.append(weekNameMap.get(day)).append("  ");
                }
                System.out.println(dayBuilder.toString());
                for (int i = 0 ;i<times.length;i++) {
                    StringBuilder timeBuilder = new StringBuilder(i+"  ");
                    for (int day : days) {
                        for (int sub : subjects) {
                            if (solver.booleanValue(lessonPoints[stu][sub][day][i])) {
                                timeBuilder.append(subNameMap.get(sub)).append("  ");
                            }
                        }
                    }
                    System.out.println(timeBuilder.toString());
                }
            }

        } else {
            System.out.printf("No optimal solution found !");
        }
        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }

}
