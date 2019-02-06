package stamina;

public class Options {

	//Probabilistic state search termination value : Defined by kappa in command line argument
	private static double reachabilityThreshold = 1e-6;
	
	// Kappa reduction factor
	private static double kappaReductionFactor = 1000.0;
	
	// max number of refinement count 
	private static int maxRefinementCount = 10;
	
	// termination Error window
	private static double probErrorWindow = 1.0e-3;
	
	// Use property based refinement
	private static boolean noPropRefine = false;
	
	
	public static double getReachabilityThreshold() {
	return reachabilityThreshold;
	}
	
	public static void setReachabilityThreshold(double reach) {
	reachabilityThreshold = reach;
	}
	
	public static double getKappaReductionFactor() {
	return kappaReductionFactor;
	}
	
	public static void setKappaReductionFactor(double fac) {
	kappaReductionFactor = fac;
	}
	
	public static int getMaxRefinementCount() {
	return maxRefinementCount;
	}
	
	public static void setMaxRefinementCount(int rc) {
	maxRefinementCount = rc;
	}
	
	public static double getProbErrorWindow() {
	return probErrorWindow;
	}
	
	public static void setProbErrorWindow(double w) {
	probErrorWindow = w;
	}
	
	public static void setNoPropRefine(boolean o) {
	noPropRefine = o;
	}
	
	public static boolean getNoPropRefine() {
	return noPropRefine;
	}

}
