package stamina;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import parser.Values;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import parser.ast.Property;
import prism.Prism;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLog;
import prism.Result;
import prism.ResultsCollection;
import prism.UndefinedConstants;

public class StaminaCL {
	
	// logs
	private PrismLog mainLog = null;

	// Stamina Object
	private StaminaModelChecker staminaMC = null;
	
	// storage for parsed model/properties files
	private String modelFilename = null;
	private String propertiesFilename = null;
	private ModulesFile modulesFile = null;
	private PropertiesFile propertiesFile = null;
	
	// info about which properties to model check
	private int numPropertiesToCheck = 0;
	private List<Property> propertiesToCheck = null;

	// info about undefined constants
	private UndefinedConstants undefinedConstants[];
	private UndefinedConstants undefinedMFConstants;
	private Values definedMFConstants;
	private Values definedPFConstants;

	// results
	private ResultsCollection results[] = null;
	
	//////////////////////// Command line options ///////////////////////
	
	// argument to -const switch
	private String constSwitch = null;
	
	//Probabilistic state search termination value : Defined by kappa in command line argument
	private double reachabilityThreshold = -1.0;
	
	// Kappa reduction factor
	private double kappaReductionFactor = -1.0;
	
	// max number of approx-refinement limit 
	private int maxApproxRefineCount = -1;
	
	// termination Error window
	private double probErrorWindow = -1.0;
	
	
	//////////////////////////////////// Command lines args to pass to prism ///////////////////
	// Solutions method max iteration
	private int maxLinearSolnIter = -1;
	
	// Solution method
	private String solutionMethod = null;
	
	

	public static void main(String[] args) {
		
		// Normal operation: just run PrismCL
		if (args.length > 0) {
			new StaminaCL().run(args);
		}
		else {
			System.err.println("Error: Missing arguments.");
		}
	}
	
	public void run(String[] args) {
		
		Result res;
		mainLog = new PrismFileLog("stdout");
		
		//Initialize
		initializeSTAMINA();
		
		// Parse options
		doParsing(args);
		
		// Process options
		processOptions();
		
		
		try {
			// process info about undefined constant
			undefinedMFConstants = new UndefinedConstants(modulesFile, null);
			
			undefinedConstants = new UndefinedConstants[numPropertiesToCheck];
			for (int i = 0; i < numPropertiesToCheck; i++) {
				undefinedConstants[i] = new UndefinedConstants(modulesFile, propertiesFile, propertiesToCheck.get(i));
			}
			
			// then set up value using const switch definitions
			undefinedMFConstants.defineUsingConstSwitch(constSwitch);
			for (int i = 0; i < numPropertiesToCheck; i++) {
				undefinedConstants[i].defineUsingConstSwitch(constSwitch);
			}
			
			

			// initialise storage for results
			results = new ResultsCollection[numPropertiesToCheck];
			for (int i = 0; i < numPropertiesToCheck; i++) {
				results[i] = new ResultsCollection(undefinedConstants[i], propertiesToCheck.get(i).getExpression().getResultName());
			}

			// iterate through as many models as necessary
			for (int i = 0; i < undefinedMFConstants.getNumModelIterations(); i++) {

				// set values for ModulesFile constants
				try {
					definedMFConstants = undefinedMFConstants.getMFConstantValues();
					staminaMC.setPRISMModelConstants(definedMFConstants);
				} catch (PrismException e) {
					// in case of error, report it, store as result for any properties, and go on to the next model
					// (might happen for example if overflow or another numerical problem is detected at this stage)
					mainLog.println("\nError: " + e.getMessage() + ".");
					for (int j = 0; j < numPropertiesToCheck; j++) {
						results[j].setMultipleErrors(definedMFConstants, null, e);
					}
					// iterate to next model
					undefinedMFConstants.iterateModel();
					for (int j = 0; j < numPropertiesToCheck; j++) {
						undefinedConstants[j].iterateModel();
					}
					continue;
				}

				//modelBuildFail = false;
				
				// Work through list of properties to be checked
				for (int j = 0; j < numPropertiesToCheck; j++) {
					
					
					for (int k = 0; k < undefinedConstants[j].getNumPropertyIterations(); k++) {
						
						try {
							// Set values for PropertiesFile constants
							if (propertiesFile != null) {
								definedPFConstants = undefinedConstants[j].getPFConstantValues();
								propertiesFile.setSomeUndefinedConstants(definedPFConstants);
							}
							
							res = staminaMC.modelCheck(propertiesFile, propertiesToCheck.get(j));
							
							
						
						} catch (PrismException e) {
							mainLog.println("\nError: " + e.getMessage() + ".");
							res = new Result(e);
						}
						
						// store result of model checking
						results[j].setResult(definedMFConstants, definedPFConstants, res.getResult());
						//results[j+1].setResult(definedMFConstants, definedPFConstants, res[1].getResult());
						
						// iterate to next property
						undefinedConstants[j].iterateProperty();
						
					}
				}
				
				// iterate to next model
				undefinedMFConstants.iterateModel();
				for (int j = 0; j < numPropertiesToCheck; j++) {
					undefinedConstants[j].iterateModel();
				}
			
			}
			
		} catch (PrismException e) {
			errorAndExit(e.getMessage());
		}
		
	}
	
	
	public void initializeSTAMINA() {
	
		//init prism
		try {
			// Create a log for PRISM output (hidden or stdout)
			//mainLog = new PrismDevNullLog();
			mainLog = new PrismFileLog("stdout");
			
			// Initialise PRISM engine 
			staminaMC = new StaminaModelChecker(mainLog);
			staminaMC.initialise();
			
			staminaMC.setEngine(Prism.EXPLICIT);
			
	
		} catch (PrismException e) {
			mainLog.println("Error: " + e.getMessage());
			System.exit(1);
		}
	}
	
	
	
	private void processOptions() {
		
		try {
			
			// Configure options
			if (reachabilityThreshold >= 0.0 )	StaminaModelChecker.Options.setReachabilityThreshold(reachabilityThreshold);
			if (maxApproxRefineCount >= 0) StaminaModelChecker.Options.setMaxApproxRefineCount(maxApproxRefineCount);
			if (probErrorWindow >= 0.0) StaminaModelChecker.Options.setProbErrorWindow(probErrorWindow);
			if (maxLinearSolnIter >= 0) staminaMC.setMaxIters(maxLinearSolnIter);
			
			if (solutionMethod != null) {
				
				if (solutionMethod.equals("power")) {
					staminaMC.setEngine(Prism.POWER);
				}
				else if (solutionMethod.equals("jacobi")) {
					staminaMC.setEngine(Prism.JACOBI);
				}
				else if (solutionMethod.equals("gaussseidel")) {
					staminaMC.setEngine(Prism.GAUSSSEIDEL);
				}
				else if (solutionMethod.equals("bgaussseidel")) {
					staminaMC.setEngine(Prism.BGAUSSSEIDEL);
				}
			}
			
			staminaMC.loadPRISMModel(modulesFile);
			
			
			
		} catch (PrismException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private void doParsing(String[] args) {
		
		parseArguments(args);
		parseModelProperties();
		
	}
	
	void parseArguments(String[] args) {
		
		String sw;
		int i;
		
		constSwitch = "";
		
		for (i=0; i<args.length; i++) {
			
			// if is a switch...
			if (args[i].length() > 0 && args[i].charAt(0) == '-') {
				
				// Remove "-"
				sw = args[i].substring(1);
				if (sw.length() == 0) {
					errorAndExit("Invalid empty switch");
				}
				
				if (sw.equals("kappa")) {
					
					if (i < args.length - 1) {
						reachabilityThreshold = Double.parseDouble(args[++i].trim());
					}
					else {
						mainLog.println("reachabilityThreshold(kappa) not defined.");
					}
					
				}
				else if (sw.equals("reducekappa")) {
					
					if (i < args.length - 1) {
						kappaReductionFactor = Double.parseDouble(args[++i].trim());
					}
					else {
						mainLog.println("kappaReductionFactor not defined.");
					}
					
				}
				else if (sw.equals("pbwin")) {
					
					if (i < args.length - 1) {
						probErrorWindow = Double.parseDouble(args[++i].trim());
					}
					else {
						mainLog.println("Probability error window not given.");
					}
					
				}
				else if (sw.equals("maxappref")) {
					
					maxApproxRefineCount = Integer.parseInt(args[++i].trim());
					
				}
				else if (sw.equals("maxiters")) {
					
					maxLinearSolnIter = Integer.parseInt(args[++i].trim());
					
				}
				else if (sw.equals("power") || sw.equals("jacobi") || sw.equals("gaussseidel") || sw.equals("bgaussseidel") ) {
					
					solutionMethod = args[++i];
					
				}
				else if (sw.equals("const")) {
					
					if (i < args.length - 1) {
						// store argument for later use (append if already partially specified)
						if ("".equals(constSwitch))
							constSwitch = args[++i].trim();
						else
							constSwitch += "," + args[++i].trim();
					}
				}
				else {
					printHelp();
					exit();
				}
				
			}
			// otherwise argument must be a filename
			else if ((modelFilename == null) && (args[i].endsWith(".prism") || args[i].endsWith(".sm") )) {
				modelFilename = args[i];
			} 
			else if ((propertiesFilename == null) && (args[i].endsWith(".csl"))) {
				propertiesFilename = args[i];
			}
			// anything else - must be something wrong with command line syntax
			else {
				errorAndExit("Invalid argument syntax");
			}
			
		}
		
	}
	
	
	
	/**
	 * parse model and properties file
	 */
	void parseModelProperties(){
		
		propertiesToCheck = new ArrayList<Property>();
		
		try {
			// Parse and load a PRISM model from a file
			modulesFile = staminaMC.parseModelFile(new File(modelFilename));

			// Parse and load a properties model for the model
			propertiesFile = staminaMC.parsePropertiesFile(modulesFile, new File(propertiesFilename));
			
			if (propertiesFile == null) {
				numPropertiesToCheck = 0;
			}
			// unless specified, verify all properties
			else{
				
				numPropertiesToCheck = propertiesFile.getNumProperties();
				for(int i = 0; i< numPropertiesToCheck; ++i) {
					propertiesToCheck.add(propertiesFile.getPropertyObject(i));
				}
				
				
			}

		} catch (FileNotFoundException e) {
			System.out.println("Error: " + e.getMessage());
			System.exit(1);
		} catch (PrismException e) {
			System.out.println("Error: " + e.getMessage());
			System.exit(1);
		}
		
	}
	
	
	/**
	 * Report a (fatal) error and exit cleanly (with exit code 1).
	 */
	private void exit()
	{
		staminaMC.closeDown();
		mainLog.flush();
		System.exit(1);
	}
	
	/**
	 * Report a (fatal) error and exit cleanly (with exit code 1).
	 */
	private void errorAndExit(String s)
	{
		staminaMC.closeDown();
		mainLog.println("\nError: " + s + ".");
		mainLog.flush();
		System.exit(1);
	}
	
	/**
	 * Print a -help message, i.e. a list of the command-line switches.
	 */
	private void printHelp()
	{
		mainLog.println("Usage: " + "InfModelCheck" + " <model-file> <properties-file> [options]");
		mainLog.println();
		mainLog.println("<model-file> .................... Prism model file. Extensions: .prism, .sm");
		mainLog.println("<properties-file> ............... Property file. Extensions: .csl");
		mainLog.println();
		mainLog.println("Options:");
		mainLog.println("========");
		mainLog.println();
		mainLog.println("-kappa <k>.......................... ReachabilityThreshold [default: 1.0e-6]");
		mainLog.println("-reducekappa <f>.................... Reduction factor for ReachabilityThreshold(kappa) for refinement step.  [default: 1000.0]");
		mainLog.println("-pbwin <e>.......................... Probability window between lower and upperbound for termination. [default: 1.0e-3]");
		mainLog.println("-maxappref <n>...................... Maximum number of approximation and refinement iteration. [default: 10]");
		mainLog.println("-maxiters <n>....................... Maximum iteration for solution. [default: 10000]");
		mainLog.println("-const <vals> ...................... Comma separated values for constants");
		mainLog.println("\tExamples:");
		mainLog.println("\t-const a=1,b=5.6,c=true");
		mainLog.println();
		mainLog.println("Other Options:");
		mainLog.println("========");
		mainLog.println();
		mainLog.println("-power .......................... Power method");
		mainLog.println("-jacobi ......................... Jacobi method");
		mainLog.println("-gaussseidel .................... Gauss-Seidel method");
		mainLog.println("-bgaussseidel ................... Backward Gauss-Seidel method");
		mainLog.println();
	}
}