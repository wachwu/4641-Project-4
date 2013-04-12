import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import rl.DecayingEpsilonGreedyStrategy;
import rl.EpsilonGreedyStrategy;
import rl.ExplorationStrategy;
import rl.MazeMarkovDecisionProcess;
import rl.MazeMarkovDecisionProcessVisualization;
import rl.Policy;
import rl.PolicyIteration;
import rl.QLambda;
import rl.ValueIteration;
import shared.FixedIterationTrainer;
import shared.ThresholdTrainer;


public class ProjectRunner {

	static String[] mazes = {"medium"};
	static String extension = ".txt";
	
	public static void main(String[] args) throws Exception {
		int minThreads = 1;
		int maxThreads = 4;
		long keepAlive = 10;
		int iterations = 50000;
		double difference = 0.1;
		LinkedBlockingQueue<Runnable> q = new LinkedBlockingQueue<Runnable>();
		ThreadPoolExecutor tpe = new ThreadPoolExecutor(minThreads, maxThreads, keepAlive, TimeUnit.SECONDS, q);
		

		for (String maze : mazes) {
			MazeMarkovDecisionProcess m = MazeMarkovDecisionProcess.load("mazes/"+maze+extension);
			double gamma = 0.95, lambda = 0.5, alpha = 0.2, decay = 1;
			// not going to iterate over gamma for the large maze. It takes long enough as it is...
			double epsilon = 0.5;
			while (epsilon > 0.1) {
				q.put(new MDPWorker(gamma, m, maze));
				q.put(new QLWorker(gamma, lambda, alpha, decay, iterations, 
						new EpsilonGreedyStrategy(epsilon), "EpsilonGreedy", m, maze, epsilon));
				q.put(new QLWorker(gamma, lambda, alpha, decay, iterations, 
						new DecayingEpsilonGreedyStrategy(epsilon, gamma), "DecayingEpsilonGreedy", m, maze, epsilon));
				
				epsilon -= difference;
				System.out.println(q.size());
			}

		}
		
		tpe.shutdown();
		while (!tpe.awaitTermination(3600, TimeUnit.MINUTES)) {
			System.out.print(".");
		}
		System.out.println("Done\n========");
		
	}
	
	private static class QLWorker implements Runnable {
		private double gamma, lambda, alpha, decay, epsilon;
		private int iterations;
		private ExplorationStrategy strategy;
		private MazeMarkovDecisionProcess maze;
		private String mazeName, stratName;
		
//		public QLWorker() {
//			this.gamma = 0.95;
//			this.lambda = 0.5;
//			this.alpha = 0.2;
//			this.decay = 1;
//			this.iterations = 50000;
//		}
		
		/**
		 * 
		 * @param gamma
		 * @param lambda
		 * @param alpha
		 * @param decay
		 * @param iters
		 * @param strat
		 * @param stratName
		 * @param m
		 * @param mazeName
		 */
		public QLWorker(double gamma, double lambda, double alpha, double decay, int iters, ExplorationStrategy strat, 
				String stratName, MazeMarkovDecisionProcess m, String mazeName, double epsilon) {
			this.gamma = gamma;
			this.lambda = lambda;
			this.alpha = alpha;
			this.decay = decay;
			this.iterations = iters;
			this.strategy = strat;
			this.stratName = stratName;
			this.maze = m;
			this.mazeName = mazeName;
			this.epsilon = epsilon;
		}
		@Override
		public void run() {
			StringBuilder res = new StringBuilder();
	        MazeMarkovDecisionProcessVisualization mazeVis =
	                new MazeMarkovDecisionProcessVisualization(maze);
			

	        res.append("Configuration:\n");
	        res.append("Strat:\t"+ this.stratName+"\n");
	        res.append("Gamma:\t"+ this.gamma+"\n");
	        res.append("Lambda:\t"+ this.lambda+"\n");
	        res.append("Alpha:\t"+ this.alpha+"\n");
	        res.append("Decay:\t"+ this.decay+"\n");
	        res.append("Epsilon:\t"+ this.epsilon+"\n");
	        res.append("Iters:\t"+ this.iterations+"\n");
	        
	        QLambda ql = new QLambda(this.lambda, this.gamma, this.alpha, this.decay, this.strategy, maze);
	        FixedIterationTrainer fit = new FixedIterationTrainer(ql, this.iterations);
	        long startTime = System.currentTimeMillis();
	        fit.train();
	        Policy p = ql.getPolicy();
	        long finishTime = System.currentTimeMillis();
	        res.append("Q lambda learned : ");
	        res.append(p);
	        res.append('\n');
	        res.append("in " + iterations + " iterations");
	        res.append("and " + (finishTime - startTime) + " ms");
	        res.append('\n');
	        res.append("Acquiring " + ql.getTotalReward() + " reward");
	        res.append('\n');
	        res.append(mazeVis.toString(p));
			
			try {
				String fname = "results/"+mazeName+"/"
						+"ql_"+this.stratName+"_g"+this.gamma+"_e"+this.epsilon+"_l"+this.lambda
						+"_a"+this.alpha+"_d"+this.decay+".txt";
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fname)));
				bw.write(res.toString());
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Writing to file failed");
			}
		}
		
	}
	

	private static class MDPWorker implements Runnable {
		private double gamma;
		private MazeMarkovDecisionProcess maze;
		private String mazeName;
		
		public MDPWorker(double gamma, MazeMarkovDecisionProcess m, String mazeName) {
			this.gamma = gamma;
			this.maze = m;
			this.mazeName = mazeName;
		}
		@Override
		public void run() {
			StringBuilder res = new StringBuilder();
			res.append("Configuration:\n");
	        res.append("Gamma:\t"+ this.gamma+"\n");
	
	        System.out.println("Value Iteration starting");
			ValueIteration vi = new ValueIteration(this.gamma, maze);
	        ThresholdTrainer tt = new ThresholdTrainer(vi);
	        long startTime = System.currentTimeMillis();
	        tt.train();
	        Policy p = vi.getPolicy();
	        long finishTime = System.currentTimeMillis();
	        res.append("Value iteration learned : " + p);
	        res.append('\n');
	        res.append("in " + tt.getIterations() + " iterations");
	        res.append("and " + (finishTime - startTime) + " ms");
	        res.append('\n');
	        MazeMarkovDecisionProcessVisualization mazeVis =
	            new MazeMarkovDecisionProcessVisualization(maze);
	        res.append(mazeVis.toString(p));
	        res.append('\n');
	        res.append('\n');
	        System.out.println("Value Iteration done");
	
	        System.out.println("Policy Iteration starting");
	        PolicyIteration pi = new PolicyIteration(this.gamma, maze);
	        tt = new ThresholdTrainer(pi);
	        startTime = System.currentTimeMillis();
	        tt.train();
	        p = pi.getPolicy();
	        finishTime = System.currentTimeMillis();
	        res.append("Policy iteration learned : " + p);
	        res.append('\n');
	        res.append("in " + tt.getIterations() + " iterations");
	        res.append("and " + (finishTime - startTime) + " ms");
	        res.append('\n');
	        res.append(mazeVis.toString(p));
	        res.append('\n');
	        res.append('\n');
	        System.out.println("Policy Iteration done");

	        
			try {
				String fname = "results/"+mazeName+"/"+"vp_"+"_g"+this.gamma+".txt";
				BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fname)));
				bw.write(res.toString());
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.out.println("Writing to file failed");
			}
		}
		
	}
}
