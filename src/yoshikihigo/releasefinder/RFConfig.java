package yoshikihigo.releasefinder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class RFConfig {

	static private RFConfig SINGLETON = null;

	static public boolean initialize(final String[] args) {

		if (null != SINGLETON) {
			return false;
		}

		final Options options = new Options();

		{
			final Option repository = new Option("repo", "repository", true,
					"repository of a target software");
			repository.setArgName("number");
			repository.setArgs(1);
			repository.setRequired(false);
			options.addOption(repository);
		}

		{
			final Option output = new Option("o", "output", true,
					"output file in CSV format");
			output.setArgName("number");
			output.setArgs(1);
			output.setRequired(false);
			options.addOption(output);
		}

		{
			final Option startrev = new Option("startrev", "startrev", true,
					"start revision");
			startrev.setArgName("revision");
			startrev.setArgs(1);
			startrev.setRequired(false);
			options.addOption(startrev);
		}

		{
			final Option endrev = new Option("endrev", "endrev", true,
					"end revision");
			endrev.setArgName("revision");
			endrev.setArgs(1);
			endrev.setRequired(false);
			options.addOption(endrev);
		}

		{
			final Option verbose = new Option("v", "verbose", false,
					"verbose output for progressing");
			verbose.setRequired(false);
			options.addOption(verbose);
		}

		{
			final Option debug = new Option("debug", "debug", false,
					"print some informlation for debugging");
			debug.setRequired(false);
			options.addOption(debug);
		}

		try {
			final CommandLineParser parser = new PosixParser();
			final CommandLine commandLine = parser.parse(options, args);
			SINGLETON = new RFConfig(commandLine);
		} catch (ParseException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return true;
	}

	static public RFConfig getInstance() {

		if (null == SINGLETON) {
			System.err.println("RFConfig is not initialized.");
			System.exit(0);
		}

		return SINGLETON;
	}

	private final CommandLine commandLine;

	private RFConfig(final CommandLine commandLine) {
		this.commandLine = commandLine;
	}

	public String getREPOSITORY() {
		if (!this.commandLine.hasOption("repo")) {
			System.err.println("option \"repo\" is not specified.");
			System.exit(0);
		}
		return this.commandLine.getOptionValue("repo");
	}

	public String getOUTPUT() {
		if (!this.commandLine.hasOption("o")) {
			System.err.println("option \"o\" is not specified.");
			System.exit(0);
		}
		return this.commandLine.getOptionValue("o");
	}

	public boolean hasSTARTREV() {
		return this.commandLine.hasOption("startrev");
	}

	public long getSTARTREV() {
		if (!this.commandLine.hasOption("startrev")) {
			System.err.println("option \"startrev\" is not specified.");
			System.exit(0);
		}
		return Long.parseLong(this.commandLine.getOptionValue("startrev"));
	}

	public boolean hasENDREV() {
		return this.commandLine.hasOption("endrev");
	}

	public long getENDREV() {
		if (!this.commandLine.hasOption("endrev")) {
			System.err.println("option \"endrev\" is not specified.");
			System.exit(0);
		}
		return Long.parseLong(this.commandLine.getOptionValue("endrev"));
	}

	public boolean isVERBOSE() {
		return this.commandLine.hasOption("v");
	}

	public boolean isDEBUG() {
		return this.commandLine.hasOption("debug");
	}
}
