public class Release implements Comparable<Release> {

	final public String name;
	final public Revision revision;

	public Release(final String name, final Revision revision){
		this.name = name;
		this.revision = revision;
	}

	@Override
	public int compareTo(final Release release) {
		return this.revision.compareTo(release.revision);
	}
}
