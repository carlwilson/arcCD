/**
 * 
 */
package org.opf_labs.arc_cd.cli;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import org.opf_labs.arc_cd.cdrdao.CdrdaoCliWrapper;
import org.opf_labs.arc_cd.cdrdao.CdrdaoCliWrapperFactory;
import org.opf_labs.arc_cd.cdrdao.CdrdaoWrapper.CdrdaoException;
import org.opf_labs.arc_cd.cdrdao.toc.TocItemRecord;
import org.opf_labs.arc_cd.collection.ArchiveCollection;
import org.opf_labs.arc_cd.collection.ArchiveItem;
import org.opf_labs.arc_cd.collection.CataloguedCd;
import org.opf_labs.arc_cd.collection.ManifestTest;
import org.opf_labs.arc_cd.collection.ManifestTest.Result;
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
	private static ArchiveCollection ARCHIVE_COLLECTION;
	private static CdrdaoCliWrapper CDRDAO;
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
		//BasicConfigurator.configure();
		setupConfiguration(args);
		outputWelcome();
		getArchiveCollection();
		getCdrdaoWrapper();
		archiveItem();
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

	private static void archiveItem() {
		CataloguedCd itemToArchive = getItemToArchive();
		File itemDir = getItemDirectory(itemToArchive.getId());
		TocItemRecord tocRecord = readCdToc();

		String tocPath = itemDir.getAbsolutePath() + File.separator
		+ itemToArchive.getFormattedId() + "." + "toc";
		File tocFile = new File(tocPath);
		if (tocFile.exists())
			tocFile.delete();
		if (tocRecord.getTracks().size() == itemToArchive.getCdDetails().getTracks().size()) {
			System.out.println("Inserted CD is id " + itemToArchive.getFormattedId());
			System.out.println("Artist is " + itemToArchive.getCdDetails().getAlbumArtist()
					+ ", and title is " + itemToArchive.getCdDetails().getTitle());
			System.out.println("Item has " + itemToArchive.getCdDetails().getTracks().size()
					+ " tracks.");
			System.out.println("OK to archive? [Y/n]");
			if (ArcCdInputProcessor.confirmChoice()) {
				try {
					CDRDAO.ripCdToBinFromDefaultCdDevice(itemDir, itemToArchive.getFormattedId());
				} catch (CdrdaoException excep) {
					// TODO Auto-generated catch block
					excep.printStackTrace();
				}
				try {
					ARCHIVE_COLLECTION.archiveItem(itemToArchive.getId().intValue());
					System.out.println("\007");
					System.out.println("\007");
					System.out.println("\007");
				} catch (IOException excep) {
					// TODO Auto-generated catch block
					excep.printStackTrace();
				}
			}
		} else if (tocRecord.getTracks().size() != itemToArchive.getCdDetails().getTracks().size()) {
			System.out.println("Inserted CD is id " + itemToArchive.getFormattedId());
			System.out.println("Artist is " + itemToArchive.getCdDetails().getAlbumArtist()
					+ ", and title is " + itemToArchive.getCdDetails().getTitle());
			System.out.println("Info record states that the item should have "
					+ itemToArchive.getCdDetails().getTracks().size() + " tracks.");
			System.out.println("Inserted CD has "
					+ tocRecord.getTracks().size() + " tracks.");
			System.out.println("Please check that the CD matches.");
		} else {
			System.out.println("CD is not inserted or has no tracks.");
		}
		
	}
	
	private static void outputWelcome() {
		if (!CdrdaoCliWrapperFactory.isCdrdaoInstalled()) {
			System.out.println("Couldn't detect cdrdao utility");
			System.exit(ERROR_STATUS);
		}
		System.out.println("Welcome to archCD, please insert a CD to archive.");
		System.out.println("cdrdao version "
				+ CdrdaoCliWrapperFactory.getInstalledVersion()
				+ " detected and running.");
	}

	private static void outputHelpAndExit(final int status) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("arcCD", ArcCdOptionsParser.getOptions());
		System.exit(status);
	}
	
	private static CataloguedCd getItemToArchive() {
		Integer itemId = ArcCdInputProcessor.getInputId();

		// Check for info file in root
		CataloguedCd infoRecord = ARCHIVE_COLLECTION.getCataloguedItem(itemId);
		
		// If no info file found then report, and list available info files
		if (infoRecord.equals(CataloguedCd.DEFAULT)) {
			LOGGER.info("No info file found for id: " + itemId);
			LOGGER.info("List of ids for info files awaiting archiving:");
			for (Integer id : ARCHIVE_COLLECTION.getCataloguedIds()) {
				LOGGER.info(id.toString());
			}
		}
		return infoRecord;
	}
	
	private static void getCdrdaoWrapper() {
		try {
			CDRDAO = CdrdaoCliWrapperFactory.getInstalledInstance();
		} catch (CdrdaoException excep) {
			throw new IllegalStateException(excep);
		}
	}

	private static void getArchiveCollection() {
		ARCHIVE_COLLECTION = new ArchiveCollection(CONFIG.getCollectionRoot());
		// if no items to archive, then terminate
		if (ARCHIVE_COLLECTION.getCataloguedIds().size() == 0) {
			LOGGER.warn("No info files for items to archive found in Collection root: " + CONFIG.getCollectionRoot());
		}
	}
	
	private static File getItemDirectory(Integer id) {
		File itemDir = new File(String.format("%s%s%05d",
				CONFIG.getCollectionRoot(), File.separator, id));
		if (!itemDir.exists()) {
			if (!itemDir.mkdirs()) {
				logFatalMessageAndTerminateWithCode("Couldn't create item  dir:" + itemDir, ERROR_STATUS);
			}
		} else if (itemDir.list().length > 0) {
			ArchiveItem archItem = ArchiveItem.fromDirectory(itemDir);
			if (archItem.isArchived()) {
				logFatalMessageAndTerminateWithCode("Archived Item " + itemDir
						+ " exists and is already valid", ERROR_STATUS);
			}
		}
		return itemDir;
	}

	private static TocItemRecord readCdToc() {
		System.out.println("Reading CD Table Of Contents [TOC].");
		TocItemRecord toc = TocItemRecord.defaultInstance();
		try {
			toc = TocItemRecord.fromInputStream(CDRDAO.readTocFromDefaultCdDevice());
		} catch (CdrdaoException | IOException excep) {
			// TODO Auto-generated catch block
			excep.printStackTrace();
			logFatalMessageAndTerminateWithCode("Fatal Exception reading CD TOC.", ERROR_STATUS);
		}
		return toc;
	}
	
	private static void checkManifests() {
		for (ArchiveItem item : ARCHIVE_COLLECTION.getArchiveItems()) {
			// System.out.println("Checking manifest for item:" + item.getInfo());
			ManifestTest manifest = item.checkManifest();
			if (!manifest.hasPassed()) {
				if (manifest.getBinResult() != Result.DELETED && manifest.getCueResult() != Result.ADDED)
				{
					System.err.println("Manifest test failed for item" + item.getId());
					System.err.println("Test Failed:" + manifest.toString());
				}
			}
		}
	}

	private static void logFatalMessageAndTerminateWithCode(
			final String message, final int exitCode) {
		LOGGER.fatal(message);
		System.exit(exitCode);
	}
}
