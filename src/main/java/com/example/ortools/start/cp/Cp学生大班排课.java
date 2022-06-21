package com.example.ortools.start.cp;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Cp学生大班排课 {

    public static void main(String[] args) {

        /**
         * 模拟学生排课
         *      时段：7天（周一到周日，周六日不上课），每天4个时段 共28个时段
         *      学生：1个
         *      频次：语文2，数学2，英语2，化学2，物理2，生物2，体育2，自习5
         *      学生请假时间：周一上午第一个时段
         *      每个科目每天只能上一次
         *      自习课都在每天的最后一节课
         */

        Loader.loadNativeLibraries();
        int dayNum=7,timeNum=4,studentNum=2,subjectNum = 8;
        // 基础数据
        int[] days = IntStream.range(0, dayNum).toArray();
        int[] times = IntStream.range(0, timeNum).toArray();
        int[] students = IntStream.range(0, studentNum).toArray();
        int[] subjects = IntStream.range(0, subjectNum).toArray();
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
        // 学生可排时段,[student][day][time]
        int[][][] studentAvailableTime  = new int[][][]{
            {
                {0,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {0,0,0,0},
                {0,0,0,0}
            },
            {
                {0,0,0,0},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {1,1,1,1},
                {0,0,0,0}
            }
        };
        // 每个科目每天最多只能上一次
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    List<Literal> list = new ArrayList<>();
                    for (Integer time : times){
                        list.add(lessonPoints[stu][sub][day][time]);
                    }
                    model.addAtMostOne(list);
                }
            }
        }

        // 每天每个时段只能上一科
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

        // 除了自习课外，每个科目最少上2次课，最多不能超过3次
        int minCount = (20-5)/7 ;
        int maxCount = (20-5)/7 + 1;
        for (Integer stu : students){
            for (Integer sub : subjects){
                if (sub != 7){
                    LinearExprBuilder builder = LinearExpr.newBuilder();
                    for (Integer day : days){
                        for (Integer time : times){
                            if (time != 3){
                                builder.add(lessonPoints[stu][sub][day][time]);
                            }
                        }
                    }
                    model.addLinearConstraint(builder,minCount,maxCount);
                }
            }
        }

        // 每天的最后一节课都是自习课
        for (Integer stu : students){
            for (Integer sub : subjects){
                LinearExprBuilder builder = LinearExpr.newBuilder();
                if (sub == 7){
                    for (Integer day : days){
                        for (int i=0;i<timeNum;i++){
                            if (i==3){
                                builder.addTerm(lessonPoints[stu][sub][day][i],1);
                            }else {
                                builder.addTerm(lessonPoints[stu][sub][day][i],-1);
                            }
                        }
                    }
                    model.addLinearConstraint(builder,2,5);
                }
            }
        }

        // 学生可排时段
        LinearExprBuilder builder2 = LinearExpr.newBuilder();
        for (Integer stu : students){
            for (Integer sub : subjects){
                for (Integer day : days){
                    for (Integer time : times){
                        if (countryAvailableTime[day][time] * studentAvailableTime[stu][day][time] == 1){
                            builder2.addTerm(lessonPoints[stu][sub][day][time],1);
                        }else {
                            builder2.addTerm(lessonPoints[stu][sub][day][time],-1);
                        }
                    }
                }
            }
            //model.addEquality(builder2,19);
        }
        model.maximize(builder2);

        // 求解
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Solution:%n");
            for (int stu : students){
                System.out.printf("学生 %d%n",stu);
                for (int day : days) {
                    for (int time : times) {
                        for (int sub : subjects) {
                            if (solver.booleanValue(lessonPoints[stu][sub][day][time])) {
                                if (studentAvailableTime[stu][day][time] == 1) {
                                    System.out.printf(" 学生 %d 科目 %d 周%d 时段 %d (requested).%n", stu,sub, day+1, time);
                                } else {
                                    System.out.printf(" 学生 %d 科目 %d 周%d 时段 %d (not requested).%n", stu,sub, day+1, time);
                                }
                            }
                        }
                    }
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
