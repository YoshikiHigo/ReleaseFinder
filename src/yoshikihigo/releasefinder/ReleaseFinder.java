package yoshikihigo.releasefinder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class ReleaseFinder {

	public static void main(final String[] args) {

		RFConfig.initialize(args);

		final ReleaseFinder finder = new ReleaseFinder();
		final SortedSet<Release> releases = finder.findRelease();
		finder.write(releases);
	}

	public SortedSet<Release> findRelease() {

		final SortedSet<Release> releases = new TreeSet<Release>();

		try {

			final String repository = RFConfig.getInstance().getREPOSITORY();
			final boolean isVerbose = RFConfig.getInstance().isVERBOSE();

			final SVNURL url = SVNURL.fromFile(new File(repository
					+ System.getProperty("file.separator") + "tags"));
			FSRepositoryFactory.setup();
			final SVNDiffClient diffClient = SVNClientManager.newInstance()
					.getDiffClient();

			final SVNRepository repo = FSRepositoryFactory.create(url);
			final long latestrev = repo.getLatestRevision();
			final Set<Long> malformedRevs = getMalformedRevisions();

			final long startrev = this.identifyStartRevision(repo,
					malformedRevs, 0, latestrev);
			final long endrev = this.identifyEndRevision(repo, malformedRevs,
					0, latestrev);
			releases.addAll(this.findRelease(diffClient, url, startrev, endrev));

		} catch (final SVNException e) {
			e.printStackTrace();
		}
		return releases;
	}

	private long identifyStartRevision(final SVNRepository repository,
			final Set<Long> malformedRevisions, final long rev1, final long rev2)
			throws SVNException {

		if (RFConfig.getInstance().isVERBOSE()) {
			System.out.println("START: " + rev1 + "--" + rev2);
		}

		if (rev1 == rev2) {
			return rev1;
		}

		final long midium = (rev1 + rev2) / 2;
		final SVNNodeKind status = repository.checkPath("", midium);
		if (status != SVNNodeKind.NONE) {
			return this.identifyStartRevision(repository, malformedRevisions,
					rev1, midium);
		} else {
			return this.identifyStartRevision(repository, malformedRevisions,
					midium + 1, rev2);
		}
	}

	private long identifyEndRevision(final SVNRepository repository,
			final Set<Long> malformedRevisions, final long rev1, final long rev2)
			throws SVNException {

		if (RFConfig.getInstance().isVERBOSE()) {
			System.out.println("END: " + rev1 + "--" + rev2);
		}

		if (rev1 == rev2) {
			return rev1;
		}

		final long midium = (rev1 + rev2) / 2;
		final SVNNodeKind status = repository.checkPath("", midium);
		if (status == SVNNodeKind.NONE) {
			return this.identifyEndRevision(repository, malformedRevisions,
					rev1, midium);
		} else {
			return this.identifyEndRevision(repository, malformedRevisions,
					midium + 1, rev2);
		}
	}

	private SortedSet<Release> findRelease(final SVNDiffClient diffClient,
			final SVNURL url, final long startrev, final long endrev)
			throws SVNException {
		
		if (RFConfig.getInstance().isVERBOSE()) {
			System.out.println("MAIN: " + startrev + " : " + endrev);
		}
		
		final SortedSet<Release> releases = new TreeSet<>();

		final long delta = endrev - startrev;
		if (delta < 1) {
			return releases;
		}

		final SVNRevision rev1 = SVNRevision.create(startrev);
		final SVNRevision rev2 = SVNRevision.create(endrev);

		diffClient.doDiffStatus(url, rev1, url, rev2, SVNDepth.IMMEDIATES,
				false, new ISVNDiffStatusHandler() {

					@Override
					public void handleDiffStatus(final SVNDiffStatus status) {

						if (SVNStatusType.STATUS_ADDED == status
								.getModificationType()) {
							if (!status.getPath().contains("/")) {
								final Revision revision = (1 == delta) ? getRevision(rev1
										.getNumber()) : new Revision(startrev,
										"", "", "");
								final String name = status.getPath();
								final Release release = new Release(name,
										revision);
								releases.add(release);
							}
						}
					}
				});

		if ((1 < delta) && !releases.isEmpty()) {
			releases.clear();
			final long medium = (startrev + endrev) / 2;
			final SortedSet<Release> releases1 = this.findRelease(diffClient,
					url, startrev, medium);
			releases.addAll(releases1);
			final SortedSet<Release> releases2 = this.findRelease(diffClient,
					url, medium, endrev);
			releases.addAll(releases2);
		}

		return releases;
	}

	private Revision getRevision(final long number) {

		final List<Revision> revisions = new ArrayList<>();

		try {
			final String repository = RFConfig.getInstance().getREPOSITORY();
			final SVNURL url = SVNURL.fromFile(new File(repository));
			FSRepositoryFactory.setup();
			final SVNRepository svnRepository = FSRepositoryFactory.create(url);

			svnRepository.log(null, number, number + 1, true, true,
					new ISVNLogEntryHandler() {
						@Override
						public void handleLogEntry(SVNLogEntry logEntry)
								throws SVNException {
							final String date = logEntry.getDate().toString();
							final String author = logEntry.getAuthor();
							final String message = logEntry.getMessage();
							final Revision revision = new Revision(number,
									date, author, message);
							revisions.add(revision);
						}
					});
		}

		catch (final SVNException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return revisions.get(0);
	}

	private void write(final SortedSet<Release> releases) {

		final String output = RFConfig.getInstance().getOUTPUT();

		try (final BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(output), "UTF-8"))) {

			writer.write("NAME, REVISION, DATE, AUTHOR, LOG");
			writer.newLine();

			for (final Release release : releases) {
				writer.write(release.name);
				writer.write(", ");
				writer.write(Long.toString(release.revision.number));
				writer.write(", \"");
				writer.write(release.revision.date);
				writer.write("\", ");
				final String author = release.revision.author;
				writer.write((null != author) ? author : "no-info");
				writer.write(", ");
				writer.write(removeLineSeparator(release.revision.message));
				writer.newLine();
			}
		}

		catch (final Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

	}

	private String removeLineSeparator(final String string) {
		final StringBuilder text = new StringBuilder();
		text.append("\"");

		try (final BufferedReader reader = new BufferedReader(new StringReader(
				string))) {
			while (true) {
				final String line = reader.readLine();
				if (null == line) {
					break;
				}

				text.append(line);
				text.append(" ");
			}
		}

		catch (final Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		text.append("\"");
		return text.toString();
	}

	private Set<Long> getMalformedRevisions() {
		final Set<Long> malformed = new HashSet<>();

		if (RFConfig.getInstance().hasMALFORMED()) {
			final String path = RFConfig.getInstance().getMALFORMED();
			try (final BufferedReader reader = new BufferedReader(
					new FileReader(path))) {

				while (true) {
					final String line = reader.readLine();
					if (null == line) {
						break;
					}

					final long revision = Long.parseLong(line);
					malformed.add(revision);
				}

			} catch (final IOException e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

		return malformed;
	}
}
