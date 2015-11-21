package yoshikihigo.releasefinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class Release implements Comparable<Release> {

	final public String name;
	final public String type;
	final public Revision revision;

	public Release(final String name, final String type, final Revision revision) {
		this.name = name;
		this.type = type;
		this.revision = revision;
	}

	@Override
	public int compareTo(final Release release) {
		return this.revision.compareTo(release.revision);
	}

	public String makeCSVLine() {
		final StringBuilder text = new StringBuilder();

		text.append(this.name);
		text.append(", ");
		text.append(Long.toString(this.revision.number));
		text.append(", ");
		text.append(this.type);
		text.append(", \"");
		text.append(this.revision.date);
		text.append("\", ");
		text.append(this.revision.copyPath);
		text.append(", ");
		text.append(this.revision.copyRevision);
		text.append(", ");

		final String author = this.revision.author;
		text.append((null != author) ? author : "no-info");
		text.append(", ");
		text.append(removeLineSeparator(this.revision.message));

		return text.toString();
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

		} catch (final IOException e) {
			e.printStackTrace();
			System.exit(0);
		}

		text.append("\"");
		return text.toString();
	}
}
