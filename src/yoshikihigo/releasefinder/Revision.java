package yoshikihigo.releasefinder;
public class Revision implements Comparable<Revision> {

	final public long number;
	final public String date;
	final public String author;
	final public String message;

	public Revision(final long number, final String date, final String author,
			final String message) {
		this.number = number;
		this.date = date;
		this.author = author;
		this.message = message;
	}

	@Override
	public int compareTo(final Revision revision) {
		if (this.number < revision.number) {
			return -1;
		} else if (this.number > revision.number) {
			return 1;
		} else {
			return 0;
		}
	}
}
