package info.openrocket.core.startup;

import info.openrocket.core.database.ComponentPresetDao;
import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.l10n.ClassBasedTranslator;
import info.openrocket.core.l10n.DebugTranslator;
import info.openrocket.core.l10n.ExceptionSuppressingTranslator;
import info.openrocket.core.l10n.Translator;

import com.google.inject.Injector;
import info.openrocket.core.preferences.ApplicationPreferences;

/**
 * A class that provides singleton instances / beans for other classes to
 * utilize.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public final class Application {
	
	private static ExceptionHandler exceptionHandler;
	
	private static Injector injector;

	// Supported Java Runtime Environment versions in which OR is allowed to run
	// (e.g. '17' for Java 17)
	public static int[] SUPPORTED_JRE_VERSIONS = { 17 };

	/**
	 * Return whether to use additional safety code checks.
	 */
	public static boolean useSafetyChecks() {
		// Currently default to false unless openrocket.debug.safetycheck is defined
		String s = System.getProperty("openrocket.debug.safetycheck");
		return s != null && !(s.equalsIgnoreCase("false") || s.equalsIgnoreCase("off"));
	}

	private static Translator getBaseTranslator() {
		if (injector == null) {
			// Occurs in some unit tests
			return new DebugTranslator(null);
		}
		return injector.getInstance(Translator.class);
	}
	
	/**
	 * Return the translator to use for obtaining translated strings.
	 *
	 * @return a translator.
	 */
	public static Translator getTranslator() {
		Translator t = getBaseTranslator();
		t = new ClassBasedTranslator(t, 1);
		t = new ExceptionSuppressingTranslator(t);
		return t;
	}
	
	/**
	 * @return the preferences
	 */
	public static ApplicationPreferences getPreferences() {
		return injector.getInstance(ApplicationPreferences.class);
	}
	
	
	/**
	 * @return the exceptionHandler
	 */
	public static ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
	
	/**
	 * @param exceptionHandler the exceptionHandler to set
	 */
	public static void setExceptionHandler(ExceptionHandler exceptionHandler) {
		Application.exceptionHandler = exceptionHandler;
	}
	
	/**
	 * Return the database of all thrust curves loaded into the system.
	 * 
	 * @deprecated Fetch the db from Guice instead.
	 */
	@Deprecated
	public static MotorDatabase getMotorSetDatabase() {
		return injector.getInstance(MotorDatabase.class);
	}
	
	/**
	 * Return the ThrustCurveMotorSetDatabase for the system.
	 * 
	 * @deprecated Fetch the db from Guice instead.
	 */
	@Deprecated
	public static ThrustCurveMotorSetDatabase getThrustCurveMotorSetDatabase() {
		return injector.getInstance(ThrustCurveMotorSetDatabase.class);
	}
	
	
	@Deprecated
	public static ComponentPresetDao getComponentPresetDao() {
		return injector.getInstance(ComponentPresetDao.class);
		
	}
	
	public static Injector getInjector() {
		return injector;
	}
	
	public static void setInjector(Injector injector) {
		Application.injector = injector;
	}

}
