/**
 * 
 */
package org.opf_labs.arc_cd.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.opf_labs.arc_cd.cdrdao.CdrdaoCliWrapper;
import org.opf_labs.arc_cd.cdrdao.CdrdaoCliWrapperFactory;
import org.opf_labs.arc_cd.cdrdao.CdrdaoWrapper.CdrdaoException;
import org.opf_labs.arc_cd.cdrdao.CdrdaoWrapper.NoCdDeviceException;
import org.opf_labs.arc_cd.cdrdao.CdrdaoWrapper.NoCdException;
import org.opf_labs.arc_cd.collection.ArchiveCollection;
import org.opf_labs.arc_cd.collection.ArchiveItem;
import org.opf_labs.arc_cd.collection.CataloguedCd;
import org.opf_labs.arc_cd.collection.CdItemRecord;
import org.opf_labs.arc_cd.collection.TocItemRecord;
import org.opf_labs.arc_cd.config.ArcCdConfig;

/**
 * Command line interface for the cdrdao based CD archiver.
 * 
 * TODO Tests for ArcCdCli.</p> TODO Implementation for ArcCdCli.</p>
 * 
 * @author <a href="mailto:carl@openplanetsfoundation.org">Carl Wilson</a>.</p>
 *         <a href="https://github.com/carlwilson">carlwilson AT github</a>.</p>
 * @version 0.1
 * 
 *          Created 27 Aug 2013:15:08:09
 */

public final class ArcCdCli {
	/** OK status */
	public static final int OK_STATUS = 0;
	/** Error status */
	public static final int ERROR_STATUS = 1;
	
	private static ArcCdConfig CONFIG = ArcCdConfig.getDefault();
	private static ArchiveCollection CD_COLLECTION;
	private static Logger LOGGER = Logger.getLogger(ArcCdCli.class);

	private ArcCdCli() {
		throw new AssertionError("Illegally in ArcCdCli default constructor.");
	}

	/**
	 * Main CLI entry point, process command line arguments into a config
	 * instance, use this to set up the collection and the cdrdao wrapper, then
	 * hand off to the keyboard input parser.
	 * 
	 * @param args
	 *            the array of string command line args
	 */
	public static void main(final String[] args) {
		// TODO Replace basic logger configuration
		BasicConfigurator.configure();
		setupConfiguration(args);

		outputWelcome();

		CD_COLLECTION = new ArchiveCollection(CONFIG.getCollectionRoot());
		CdrdaoCliWrapper wrapper;
		try {
			wrapper = CdrdaoCliWrapperFactory.getInstalledInstance();
		} catch (CdrdaoException excep3) {
			throw new IllegalStateException();
		}

		Integer itemId = ArcCdInputProcessor.getInputId();
		String formattedId = CataloguedCd.formatIdToString(itemId);

		// Check for info file in root
		CdItemRecord item;
		try {
			item = CD_COLLECTION.getItemRecord(itemId);
		} catch (FileNotFoundException excep) {
			logFatalMessageAndTerminateWithCode(
					"No info file "
							+ String.format("%s%s%05d.info",
									CONFIG.getCollectionRoot(), File.separator,
									itemId) + " found, please create one.",
					ERROR_STATUS);
			return;
		}

		File itemDir = new File(String.format("%s%s%05d",
				CONFIG.getCollectionRoot(), File.separator, itemId));
		if (!itemDir.exists()) {
			if (!itemDir.mkdirs()) {
				System.out.println("Couldn't create item  dir:" + itemDir);
				System.exit(ERROR_STATUS);
			}
		} else if (itemDir.list().length > 0) {
			try {
				ArchiveItem archItem = ArchiveItem.fromDirectory(itemDir);
				if (archItem.isArchived()) {
					System.out.println("Item " + itemDir
							+ " exists and is already valid");
					System.exit(ERROR_STATUS);
				}
			} catch (FileNotFoundException excep) {
				// TODO Auto-generated catch block
				excep.printStackTrace();
			}
		}

		System.out.println("Reading CD contents.");
		String tocPath = itemDir.getAbsolutePath() + File.separator
				+ formattedId + "." + "toc";
		try {
			wrapper.readTocFromDefaultCdDevice();
		} catch (CdrdaoException excep) {
			// TODO Auto-generated catch block
			excep.printStackTrace();
		}
		File tocFile = new File(tocPath);
		try {
			TocItemRecord tocRecord = TocItemRecord.fromTocFile(tocFile);
			if (tocFile.exists())
				tocFile.delete();
			if (tocRecord.getTracks().size() == item.getTracks().size()) {
				System.out.println("Inserted CD is id " + formattedId);
				System.out.println("Artist is " + item.getAlbumArtist()
						+ ", and title is " + item.getTitle());
				System.out.println("Item has " + item.getTracks().size()
						+ " tracks.");
				System.out.println("OK to archive? [Y/n]");
				if (ArcCdInputProcessor.confirmChoice()) {
					try {
						wrapper.ripCdToBinFromDefaultCdDevice(itemDir, formattedId);
					} catch (CdrdaoException excep) {
						// TODO Auto-generated catch block
						excep.printStackTrace();
					}
					try {
						CD_COLLECTION.archiveItem(itemId.intValue());
					} catch (IOException excep) {
						// TODO Auto-generated catch block
						excep.printStackTrace();
					}
				}
			} else if (tocRecord.getTracks().size() != item.getTracks().size()) {
				System.out.println("Inserted CD is id " + formattedId);
				System.out.println("Artist is " + item.getAlbumArtist()
						+ ", and title is " + item.getTitle());
				System.out.println("Info record states that the item should have "
						+ item.getTracks().size() + " tracks.");
				System.out.println("Inserted CD has "
						+ tocRecord.getTracks().size() + " tracks.");
				System.out.println("Please check that the CD matches.");
			} else {
				System.out.println("CD is not inserted or has no tracks.");
			}
		} catch (IOException excep2) {
			// TODO Auto-generated catch block
			excep2.printStackTrace();
		}
	}

	private static void setupConfiguration(final String args[]) {
		parseConfiguration(args);
		checkHelpRequested();
		validateConfiguration();
	}
	
	private static void parseConfiguration(final String args[]) {
		try {
			CONFIG = ArcCdOptionsParser.parseConfigFromCommandLineArgs(args);
		} catch (ParseException excep) {
			LOGGER.fatal("Fatal exception parsing command line arguments.");
			LOGGER.fatal(excep.getStackTrace());
			LOGGER.fatal(excep.getMessage());
			outputHelpAndExit(ERROR_STATUS);
		}
	}
	
	private static void checkHelpRequested() {
		if (CONFIG.helpRequested()) {
			outputHelpAndExit(OK_STATUS);
		}
	}

	private static void validateConfiguration() {
		if (!ensureCollectionRootExists()) {
			logFatalMessageAndTerminateWithCode("Collection root directory "
					+ CONFIG.getCollectionRoot() + " couldn't be created.",
					ERROR_STATUS);
		}
	}
	
	private static boolean ensureCollectionRootExists() {
		File collectionRoot = new File(CONFIG.getCollectionRoot());

		if (collectionRoot.isFile()) {
			LOGGER.error("Collection root:" + CONFIG.getCollectionRoot()
					+ " should be an existing directory NOT a file.");
			return false;
		} else if (!collectionRoot.exists()) {
			return collectionRoot.mkdirs();
		}
		return true;
	}

	private static void outputWelcome() {
		if (!CdrdaoCliWrapperFactory.isCdrdaoInstalled()) {
			System.out.println("Couldn't detect cdrdao utility");
			System.exit(ERROR_STATUS);
		}
		System.out.println("Welcome to archCD, please insert a CD to archive.");
		System.out.println("cdrdao version " + CdrdaoCliWrapperFactory.getInstalledVersion() 
				+ " detected and running.");
	}

	private static void outputHelpAndExit(final int status) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("arcCD", ArcCdOptionsParser.getOptions());
		System.exit(status);
	}

	private static void logFatalMessageAndTerminateWithCode(
			final String message, final int exitCode) {
		LOGGER.fatal(message);
		System.exit(exitCode);
	}
}
