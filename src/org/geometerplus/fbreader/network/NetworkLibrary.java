/*
 * Copyright (C) 2010-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.network;

import java.util.*;

import org.geometerplus.zlibrary.core.library.ZLibrary;
import org.geometerplus.zlibrary.core.util.ZLNetworkUtil;
import org.geometerplus.zlibrary.core.options.ZLStringOption;
import org.geometerplus.zlibrary.core.network.ZLNetworkManager;
import org.geometerplus.zlibrary.core.network.ZLNetworkException;
import org.geometerplus.zlibrary.core.network.ZLNetworkRequest;
import org.geometerplus.zlibrary.core.language.ZLLanguageUtil;

import org.geometerplus.fbreader.tree.FBTree;
import org.geometerplus.fbreader.network.tree.*;
import org.geometerplus.fbreader.network.opds.OPDSCustomLink;
import org.geometerplus.fbreader.network.opds.OPDSLinkReader;
import org.geometerplus.fbreader.network.urlInfo.*;

public class NetworkLibrary {
	private static NetworkLibrary ourInstance;

	public static NetworkLibrary Instance() {
		if (ourInstance == null) {
			ourInstance = new NetworkLibrary();
		}
		return ourInstance;
	}

	private static class LinksComparator implements Comparator<INetworkLink> {
		private static String filterLinkTitle(String title) {
			for (int index = 0; index < title.length(); ++index) {
				final char ch = title.charAt(index);
				if (ch < 128 && Character.isLetter(ch)) {
					return title.substring(index);
				}
			}
			return title;
		}

		private static int languageOrder(String language) {
			if (language == ZLLanguageUtil.MULTI_LANGUAGE_CODE) {
				return 1;
			}
			if (language.equals(Locale.getDefault().getLanguage())) {
				return 0;
			}
			return 2;
		}

		public int compare(INetworkLink link1, INetworkLink link2) {
			final int languageOrder1 = languageOrder(link1.getLanguage());
			final int languageOrder2 = languageOrder(link2.getLanguage());
			if (languageOrder1 != languageOrder2) {
				return languageOrder1 - languageOrder2;
			}
			final String title1 = filterLinkTitle(link1.getTitle());
			final String title2 = filterLinkTitle(link2.getTitle());
			return title1.compareToIgnoreCase(title2);
		}
	}

	
	public interface OnNewLinkListener {
		void onNewLink(INetworkLink link);
	}

	public final ZLStringOption NetworkSearchPatternOption =
		new ZLStringOption("NetworkSearch", "Pattern", "");

	// that's important to keep this list synchronized
	// it can be used from background thread
	private final List<INetworkLink> myLinks =
		Collections.synchronizedList(new ArrayList<INetworkLink>());

	public List<String> languageCodes() {
		final TreeSet<String> languageSet = new TreeSet<String>();
		for (INetworkLink link : myLinks) {
			languageSet.add(link.getLanguage());
		}
		return new ArrayList<String>(languageSet);
	}

	private ZLStringOption myActiveLanguageCodesOption;
	private ZLStringOption activeLanguageCodesOption() {
 		if (myActiveLanguageCodesOption == null) {
			final TreeSet<String> defaultCodes = new TreeSet<String>(new ZLLanguageUtil.CodeComparator());
			defaultCodes.addAll(ZLibrary.Instance().defaultLanguageCodes());
			myActiveLanguageCodesOption =
				new ZLStringOption(
					"Options",
					"ActiveLanguages",
					commaSeparatedString(defaultCodes)
				);
		}
		return myActiveLanguageCodesOption;
	}

	public Collection<String> activeLanguageCodes() {
		return Arrays.asList(activeLanguageCodesOption().getValue().split(","));
	}

	public void setActiveLanguageCodes(Collection<String> codes) {
		final TreeSet<String> allCodes = new TreeSet<String>(new ZLLanguageUtil.CodeComparator());
		allCodes.addAll(ZLibrary.Instance().defaultLanguageCodes());
		allCodes.removeAll(languageCodes());
		allCodes.addAll(codes);
		activeLanguageCodesOption().setValue(commaSeparatedString(allCodes));
		invalidateChildren();
	}

	private String commaSeparatedString(Collection<String> codes) {
		final StringBuilder builder = new StringBuilder();
		for (String code : codes) {
			builder.append(code);
			builder.append(",");
		}
		if (builder.length() > 0) {
			builder.delete(builder.length() - 1, builder.length());
		}
		return builder.toString();
	}

	private List<INetworkLink> activeLinks() {
		final LinkedList<INetworkLink> filteredList = new LinkedList<INetworkLink>();
		final Collection<String> codes = activeLanguageCodes();
		for (INetworkLink link : myLinks) {
			if (link instanceof ICustomNetworkLink ||
				codes.contains(link.getLanguage())) {
				filteredList.add(link);
			}
		}
		return filteredList;
	}

	private final RootTree myRootTree = new RootTree("@Root");
	private final RootTree myFakeRootTree = new RootTree("@FakeRoot");
	private SearchItemTree mySearchItemTree;

	private boolean myChildrenAreInvalid = true;
	private boolean myUpdateVisibility;

	private NetworkLibrary() {
	}

	private boolean myIsAlreadyInitialized;
	public synchronized void initialize() throws ZLNetworkException {
		if (myIsAlreadyInitialized) {
			return;
		}

		try {
			OPDSLinkReader.loadOPDSLinks(new OnNewLinkListener() {
				public void onNewLink(INetworkLink link) {
					myLinks.add(link);
				}
			});
		} catch (ZLNetworkException e) {
			removeAllLoadedLinks();
			throw e;
		}

		final NetworkDatabase db = NetworkDatabase.Instance();
		if (db != null) {
			db.loadCustomLinks(
				new NetworkDatabase.ICustomLinksHandler() {
					public void handleCustomLinkData(int id, String siteName,
							String title, String summary, UrlInfoCollection<UrlInfoWithDate> infos) {
						if (title != null &&
							siteName != null &&
							infos.getInfo(UrlInfo.Type.Catalog) != null) {
							final ICustomNetworkLink link = new OPDSCustomLink(
								id, siteName, title, summary, infos
							);
							myLinks.add(link);
						}
					}
				}
			);
		}

		myIsAlreadyInitialized = true;
	}

	private void removeAllLoadedLinks() {
		final LinkedList<INetworkLink> toRemove = new LinkedList<INetworkLink>();
		for (INetworkLink link : myLinks) {
			if (!(link instanceof ICustomNetworkLink)) {
				toRemove.add(link);
			}
		}
		myLinks.removeAll(toRemove);
	}

	/*private void testDate(ATOMDateConstruct date1, ATOMDateConstruct date2) {
		String sign = " == ";
		final int diff = date1.compareTo(date2);
		if (diff > 0) {
			sign = " > ";
		} else if (diff < 0) {
			sign = " < ";
		}
		Log.w("FBREADER", "" + date1 + sign + date2);
	}*/

	private ArrayList<INetworkLink> myBackgroundLinks;
	private Object myBackgroundLock = new Object();

	// This method must be called from background thread
	public void runBackgroundUpdate(boolean clearCache) throws ZLNetworkException {
		synchronized (myBackgroundLock) {
			myBackgroundLinks = new ArrayList<INetworkLink>();

			try {
				OPDSLinkReader.loadOPDSLinks(new OnNewLinkListener() {
					public void onNewLink(INetworkLink link) {
						myBackgroundLinks.add(link);
					}
				});
			} catch (ZLNetworkException e) {
				myBackgroundLinks = null;
				throw e;
			} finally {
				if (myBackgroundLinks != null) {
					if (myBackgroundLinks.isEmpty()) {
						myBackgroundLinks = null;
					}
				}
			}
			// we create this copy to prevent long operations on synchronized list
			final List<INetworkLink> linksCopy = new ArrayList<INetworkLink>(myLinks);
			for (INetworkLink link : linksCopy) {
				if (link instanceof ICustomNetworkLink) {
					final ICustomNetworkLink customLink = (ICustomNetworkLink)link;
					if (customLink.isObsolete(12 * 60 * 60 * 1000)) { // 12 hours
						customLink.reloadInfo(true);
						NetworkDatabase.Instance().saveCustomLink(customLink);
					}
				}
			}
		}
	}

	// This method MUST be called from main thread
	// This method has effect only when runBackgroundUpdate method has returned null.
	//
	// synchronize() method MUST be called after this method
	public void finishBackgroundUpdate() {
		synchronized (myBackgroundLock) {
			if (myBackgroundLinks != null) {
				removeAllLoadedLinks();
				myLinks.addAll(myBackgroundLinks);
			}
			invalidateChildren();
		}
	}


	public String rewriteUrl(String url, boolean externalUrl) {
		final String host = ZLNetworkUtil.hostFromUrl(url).toLowerCase();
		for (INetworkLink link : myLinks) {
			if (host.contains(link.getSiteName())) {
				url = link.rewriteUrl(url, externalUrl);
			}
		}
		return url;
	}

	private void invalidateChildren() {
		myChildrenAreInvalid = true;
	}

	public void invalidateVisibility() {
		myUpdateVisibility = true;
	}

	private static boolean linkIsChanged(INetworkLink link) {
		return
			link instanceof ICustomNetworkLink &&
			((ICustomNetworkLink)link).hasChanges();
	}

	private static void makeValid(INetworkLink link) {
		if (link instanceof ICustomNetworkLink) {
			((ICustomNetworkLink)link).resetChanges();
		}
	}

	private void makeUpToDate() {
		final LinkedList<FBTree> toRemove = new LinkedList<FBTree>();

		ListIterator<FBTree> nodeIterator = myRootTree.subTrees().listIterator();
		FBTree currentNode = null;
		int nodeCount = 0;

		final ArrayList<INetworkLink> links = new ArrayList<INetworkLink>(activeLinks());
		Collections.sort(links, new LinksComparator());
		for (int i = 0; i < links.size(); ++i) {
			INetworkLink link = links.get(i);
			boolean processed = false;
			while (currentNode != null || nodeIterator.hasNext()) {
				if (currentNode == null) {
					currentNode = nodeIterator.next();
				}
				if (!(currentNode instanceof NetworkCatalogTree)) {
					toRemove.add(currentNode);
					currentNode = null;
					++nodeCount;
					continue;
				}
				final INetworkLink nodeLink = ((NetworkCatalogTree)currentNode).Item.Link;
				if (link == nodeLink) {
					if (linkIsChanged(link)) {
						toRemove.add(currentNode);
					} else {
						processed = true;
					}
					currentNode = null;
					++nodeCount;
					break;
				} else {
					INetworkLink newNodeLink = null;
					for (int j = i; j < links.size(); ++j) {
						final INetworkLink jlnk = links.get(j);
						if (nodeLink == jlnk) {
							newNodeLink = jlnk;
							break;
						}
					}
					if (newNodeLink == null || linkIsChanged(nodeLink)) {
						toRemove.add(currentNode);
						currentNode = null;
						++nodeCount;
					} else {
						break;
					}
				}
			}
			if (!processed) {
				makeValid(link);
				final int nextIndex = nodeIterator.nextIndex();
				new NetworkCatalogRootTree(myRootTree, link, nodeCount++).Item.onDisplayItem();
				nodeIterator = myRootTree.subTrees().listIterator(nextIndex + 1);
			}
		}

		while (currentNode != null || nodeIterator.hasNext()) {
			if (currentNode == null) {
				currentNode = nodeIterator.next();
			}
			toRemove.add(currentNode);
			currentNode = null;
		}

		for (FBTree tree : toRemove) {
			tree.removeSelf();
		}
		new AddCustomCatalogItemTree(myRootTree);
		mySearchItemTree = new SearchItemTree(myRootTree, 0);
	}

	private void updateVisibility() {
		for (FBTree tree : myRootTree.subTrees()) {
			if (tree instanceof NetworkCatalogTree) {
				((NetworkCatalogTree)tree).updateVisibility();
			}
		}
	}

	public void synchronize() {
		if (myChildrenAreInvalid) {
			myChildrenAreInvalid = false;
			makeUpToDate();
		}
		if (myUpdateVisibility) {
			myUpdateVisibility = false;
			updateVisibility();
		}
	}

	public NetworkTree getRootTree() {
		return myRootTree;
	}

	public SearchItemTree getSearchItemTree() {
		return mySearchItemTree;
	}

	public NetworkCatalogTree getFakeCatalogTree(NetworkCatalogItem item) {
		final String id = item.getStringId();
		for (FBTree tree : myFakeRootTree.subTrees()) {
			final NetworkCatalogTree ncTree = (NetworkCatalogTree)tree;
			if (id.equals(ncTree.getUniqueKey().Id)) {
				return ncTree;
			}
		}
		return new NetworkCatalogTree(myFakeRootTree, item, 0);
	}

	public NetworkTree getTreeByKey(NetworkTree.Key key) {
		if (key == null) {
			return null;
		}
		if (key.Parent == null) {
			if (key.equals(myRootTree.getUniqueKey())) {
				return myRootTree;
			}
			if (key.equals(myFakeRootTree.getUniqueKey())) {
				return myFakeRootTree;
			}
			return null;
		}
		final NetworkTree parentTree = getTreeByKey(key.Parent);
		if (parentTree == null) {
			return null;
		}
		for (FBTree tree : parentTree.subTrees()) {
			final NetworkTree nTree = (NetworkTree)tree;
			if (key.equals(nTree.getUniqueKey())) {
				return nTree;
			}
		}
		return null;
	}

	public void simpleSearch(String pattern, final NetworkOperationData.OnNewItemListener listener) throws ZLNetworkException {
		LinkedList<ZLNetworkRequest> requestList = new LinkedList<ZLNetworkRequest>();
		LinkedList<NetworkOperationData> dataList = new LinkedList<NetworkOperationData>();

		final NetworkOperationData.OnNewItemListener synchronizedListener = new NetworkOperationData.OnNewItemListener() {
			public synchronized void onNewItem(INetworkLink link, NetworkItem item) {
				listener.onNewItem(link, item);
			}
			public synchronized boolean confirmInterrupt() {
				return listener.confirmInterrupt();
			}
			public synchronized void commitItems(INetworkLink link) {
				listener.commitItems(link);
			}
		};

		for (INetworkLink link : activeLinks()) {
			final NetworkOperationData data = link.createOperationData(synchronizedListener);
			final ZLNetworkRequest request = link.simpleSearchRequest(pattern, data);
			if (request != null) {
				dataList.add(data);
				requestList.add(request);
			}
		}

		while (requestList.size() != 0) {
			ZLNetworkManager.Instance().perform(requestList);

			requestList.clear();

			if (listener.confirmInterrupt()) {
				return;
			}
			for (NetworkOperationData data : dataList) {
				ZLNetworkRequest request = data.resume();
				if (request != null) {
					requestList.add(request);
				}
			}
		}
	}

	public void addCustomLink(ICustomNetworkLink link) {
		final int id = link.getId();
		if (id == ICustomNetworkLink.INVALID_ID) {
			myLinks.add(link);
		} else {
			for (int i = myLinks.size() - 1; i >= 0; --i) {
				final INetworkLink l = myLinks.get(i);
				if (l instanceof ICustomNetworkLink &&
					((ICustomNetworkLink)l).getId() == id) {
					myLinks.set(i, link);
					break;
				}
			}
		}
		NetworkDatabase.Instance().saveCustomLink(link);
		invalidateChildren();
	}

	public void removeCustomLink(ICustomNetworkLink link) {
		myLinks.remove(link);
		NetworkDatabase.Instance().deleteCustomLink(link);
		invalidateChildren();
	}
}
