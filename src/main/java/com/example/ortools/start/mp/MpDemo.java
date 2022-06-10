package com.example.ortools.start.mp;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

public class MpDemo {


    /**
     * 在以下约束条件下最大化 3 x + y ：
     * 0	≤	X	≤	1
     * 0	≤	Y	≤	2
     * x + y	≤	2
     *
     * 此示例中的目标函数是 3 x  +  y。目标函数和约束都由线性表达式给出，这使得这是一个线性问题。
     */

    public static void main(String[] args) {

        Loader.loadNativeLibraries();

        // 创建一个线性规划求解器
        MPSolver solver = MPSolver.createSolver("GLOP");

        // 创建变量
        MPVariable x = solver.makeNumVar(0, 1, "x");
        MPVariable y = solver.makeNumVar(0, 2, "y");
        System.out.println("Number of variables = " + solver.numVariables());

        // 约束：x + y	≤	2
        MPConstraint ct = solver.makeConstraint(0, 2, "ct");
        ct.setCoefficient(x,1);
        ct.setCoefficient(y,1);
        System.out.println("Number of constraints = " + solver.numConstraints());

        // 目标：求最大值 3 x + y
        MPObjective objective = solver.objective();
        objective.setCoefficient(x,3);
        objective.setCoefficient(y,1);
        objective.setMaximization();

        solver.solve();

        System.out.println("Solution:");
        System.out.println("Objective value = " + objective.value());
        System.out.println("x = " + x.solutionValue());
        System.out.println("y = " + y.solutionValue());
    }



}
