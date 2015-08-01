package org.javastack.jrinetd;

public class StickyKey {
	public static final StickyKey DEFAULT = new StickyKey(0, 0);

	public final long clusterId;
	public final long stickyId;

	private StickyKey(final long clusterId, final long stickyId) {
		this.clusterId = (clusterId <= 0 ? 0 : clusterId);
		this.stickyId = (stickyId <= 0 ? 0 : stickyId);
	}

	public static StickyKey valueOf(final String clusterName, final String stickyName) {
		final long clusterId = (clusterName == null ? 0 : IOHelper.longIdFromString(clusterName));
		final long stickyId = IOHelper.longIdFromString(stickyName);
		if (clusterName != null) {
			Log.info(StickyKey.class.getSimpleName(), "Mapped clusterName=" + clusterName + " clusterId=" + clusterId);
		}
		if (stickyName != null) {
			Log.info(StickyKey.class.getSimpleName(), "Mapped stickyName=" + stickyName + " stickyId=" + stickyId);
		}
		return valueOf(clusterId, stickyId);
	}

	public static StickyKey valueOf(final long clusterId, final long stickyId) {
		return new StickyKey(clusterId, stickyId);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof StickyKey) {
			final StickyKey o = (StickyKey) obj;
			return ((this.clusterId == o.clusterId) && (this.stickyId == o.stickyId));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (int) (this.clusterId ^ (this.clusterId >>> 32) ^ this.stickyId ^ (this.stickyId >>> 32));
	}

	@Override
	public String toString() {
		return super.toString() + "[clusterId=" + clusterId + " stickyId=" + stickyId + "]";
	}
}
