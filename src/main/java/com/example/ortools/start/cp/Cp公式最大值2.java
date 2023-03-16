package com.example.ortools.start.cp;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.google.ortools.util.Domain;

import java.util.List;

public class Cp公式最大值2 {

    public static void main(String[] args) {
        /**
         * Maximize 2x + 2y + 3z subject to the following constraints:
         * x + 7⁄2 y + 3⁄2 z	≤	25
         * 3x - 5y + 7z	≤	45
         * 5x + 2y - 6z	≤	37
         * x, y, z	≥	0
         * x, y, z integers
         */


        Loader.loadNativeLibraries();


        CpModel model = new CpModel();
        IntVar x = model.newIntVar(0, 50, "x");
        IntVar y = model.newIntVar(0, 50, "y");
        IntVar z = model.newIntVar(0, 50, "z");


        model.addLessOrEqual(LinearExpr.weightedSum(new LinearArgument[]{x,y,z},new long[]{2,7,3}),50);
        model.addLessOrEqual(LinearExpr.weightedSum(new LinearArgument[]{x,y,z},new long[]{2,-5,7}),45);
        model.addLessOrEqual(LinearExpr.weightedSum(new LinearArgument[]{x,y,z},new long[]{5,2,-6}),37);

        model.maximize(LinearExpr.weightedSum(new LinearArgument[]{x,y,z},new long[]{2,2,3}));

        //model.exportToFile("C:\\Users\\jiabing\\Desktop\\test\\pk2cpModel.txt");

        CpSolver solver = new CpSolver();
        SatParameters.Builder parameters = solver.getParameters();
        parameters.setLogSubsolverStatistics(true);
        parameters.setLogFrequencyInSeconds(0.001);
        CpSolverStatus status = solver.solve(model);


        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Maximum of objective function: %f%n", solver.objectiveValue());
            System.out.println("x = " + solver.value(x));
            System.out.println("y = " + solver.value(y));
            System.out.println("z = " + solver.value(z));
        } else {
            System.out.println("No solution found.");
        }

    }



}
