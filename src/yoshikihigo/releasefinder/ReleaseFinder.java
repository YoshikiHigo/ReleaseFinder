package yoshikihigo.releasefinder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
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
		final SortedSet<Release> tagReleases = finder.findRelease("tags");
		final SortedSet<Release> branchReleases = finder
				.findRelease("branches");
		final SortedSet<Release> releases = new TreeSet<Release>();
		releases.addAll(tagReleases);
		releases.addAll(branchReleases);
		finder.write(releases);
	}

	public SortedSet<Release> findRelease(final String type) {

		final SortedSet<Release> releases = new TreeSet<Release>();

		try {

			final String repository = RFConfig.getInstance().getREPOSITORY();
			final boolean isVerbose = RFConfig.getInstance().isVERBOSE();

			final SVNURL url = SVNURL.fromFile(new File(repository
					+ System.getProperty("file.separator") + type));
			FSRepositoryFactory.setup();
			final SVNDiffClient diffClient = SVNClientManager.newInstance()
					.getDiffClient();

			final SVNRepository repo = FSRepositoryFactory.create(url);
			final long latestrev = repo.getLatestRevision();
			final Set<Long> malformedRevs = getMalformedRevisions();

			final long startrev = this.identifyStartRevision(repo,
					malformedRevs, 0, latestrev);
			final long endrev = this.identifyEndRevision(repo, malformedRevs,
					startrev, latestrev);
			releases.addAll(this.findRelease(repo, diffClient, url, startrev,
					endrev, type));

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

		if (rev2 == rev1) {
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

	private SortedSet<Release> findRelease(final SVNRepository repository,
			final SVNDiffClient diffClient, final SVNURL url,
			final long startrev, final long endrev, final String type)
			throws SVNException {

		if (RFConfig.getInstance().isVERBOSE()) {
			System.out.println("MAIN: " + startrev + " : " + endrev);
		}

		final SortedSet<Release> releases = new TreeSet<>();

		final long delta = endrev - startrev;
		if (delta < 1) {
			return releases;
		}

		if ((SVNNodeKind.NONE == repository.checkPath("", startrev))
				|| (SVNNodeKind.NONE == repository.checkPath("", endrev))) {
			return releases;
		}

		final SVNRevision rev1 = SVNRevision.create(startrev);
		final SVNRevision rev2 = SVNRevision.create(endrev);
		diffClient.doDiffStatus(url, rev1, url, rev2, SVNDepth.IMMEDIATES,
				false, new ISVNDiffStatusHandler() {

					@Override
					public void handleDiffStatus(final SVNDiffStatus status) {

						if (SVNStatusType.STATUS_ADDED != status
								.getModificationType()) {
							return;
						}

						final String path = status.getPath();
						System.out.println(delta + " : " + startrev + " : "
								+ endrev + " : " + path);
						final Revision revision = (1 == delta) ? getRevision(
								url, startrev, path) : new Revision(startrev,
								"", null, -1, "", "");
						final Release release = new Release(path, type,
								revision);
						releases.add(release);
					}
				});

		if ((1 < delta) && !releases.isEmpty()) {
			releases.clear();
			final long medium = (startrev + endrev) / 2;
			final SortedSet<Release> releases1 = this.findRelease(repository,
					diffClient, url, startrev, medium, type);
			releases.addAll(releases1);
			final SortedSet<Release> releases2 = this.findRelease(repository,
					diffClient, url, medium, endrev, type);
			releases.addAll(releases2);
		}

		return releases;
	}

	private Revision getRevision(final SVNURL url, final long number,
			final String path) {

		final List<Revision> revisions = new ArrayList<>();

		try {
			FSRepositoryFactory.setup();
			final SVNRepository svnRepository = FSRepositoryFactory.create(url);

			svnRepository.log(new String[] { path }, number, number + 1, true,
					true, new ISVNLogEntryHandler() {

						@Override
						public void handleLogEntry(SVNLogEntry logEntry)
								throws SVNException {
							final Entry<String, SVNLogEntryPath> shortestPathEntry = getShortestLogEntryPath(logEntry
									.getChangedPaths());
							final String date = logEntry.getDate().toString();
							final String copyPath = shortestPathEntry
									.getValue().getCopyPath();
							final long copyRevision = shortestPathEntry
									.getValue().getCopyRevision();
							final String author = logEntry.getAuthor();
							final String message = logEntry.getMessage();
							final Revision revision = new Revision(number,
									date, copyPath, copyRevision, author,
									message);
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

	private Entry<String, SVNLogEntryPath> getShortestLogEntryPath(
			final Map<String, SVNLogEntryPath> paths) {
		final List<Entry<String, SVNLogEntryPath>> list = new ArrayList<>(
				paths.entrySet());
		Collections.sort(
				list,
				(e1, e2) -> Integer.valueOf(e1.getKey().length()).compareTo(
						Integer.valueOf(e2.getKey().length())));
		return list.get(0);
	}

	private void write(final SortedSet<Release> tagReleases) {

		final String output = RFConfig.getInstance().getOUTPUT();

		try (final BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(output), "UTF-8"))) {

			writer.write("NAME, REVISION, TYPE, DATE, COPY_PATH, COPY_REVISION, AUTHOR, LOG");
			writer.newLine();
			for (final Release release : tagReleases) {
				final String line = release.makeCSVLine();
				writer.write(line);
				writer.newLine();
			}
		}

		catch (final Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
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
