package org.eclipse.xtext.resource;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Function;

import org.eclipse.emf.common.notify.NotificationChain;
import org.eclipse.emf.common.util.AbstractEList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class ParallelResourceSet extends XtextResourceSet {

	private final ExecutorService loadingPool;
	private final ExecutorService linkingPool;
	private final ReentrantReadWriteLock reentrantReadWriteLock;
	private final ReadLock readLock;
	private final WriteLock writeLock;
	private final Map<Resource, Future<Resource>> nowLoading;

	@Inject
	public ParallelResourceSet(ThreadPools threadPools) {
		this.loadingPool = threadPools.loadingPool;
		this.linkingPool = threadPools.linkingPool;
		this.reentrantReadWriteLock = new ReentrantReadWriteLock();
		this.readLock = reentrantReadWriteLock.readLock();
		this.writeLock = reentrantReadWriteLock.writeLock();
		setURIResourceMap(new ConcurrentHashMap<>());
		this.normalizationMap = new ConcurrentHashMap<>();
		this.nowLoading = new ConcurrentHashMap<>();
	}

	public <Result> List<Result> processResources(List<Resource> resources,
			Function<? super Resource, ? extends Result> fun) {
		List<Future<Result>> futures = Lists.newArrayList();
		for (Resource resource : resources) {
			futures.add(linkingPool.submit(() -> fun.apply(resource)));
		}
		List<Result> result = Lists.newArrayList();
		for (Future<Result> future : futures) {
			try {
				result.add(future.get());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	public List<Resource> getResources(List<URI> uris, boolean loadOnDemand) {
		List<Future<Resource>> futures = Lists.newArrayList();

		for (URI uri : uris) {
			Map<URI, Resource> map = getURIResourceMap();
			if (map == null || resourceLocator != null) {
				futures.add(CompletableFuture.completedFuture(super.getResource(uri, loadOnDemand)));
				continue;
			}
			Resource resource = map.get(uri);
			if (resource == null) {
				URI normalizedURI = getURIConverter().normalize(uri);
				resource = map.get(normalizedURI);
				if (resource != null) {
					normalizationMap.put(uri, normalizedURI);
				}
			}
			if (resource != null) {
				if (loadOnDemand && !resource.isLoaded()) {
					demandLoadHelper(resource);
				}
				futures.add(nowLoading.getOrDefault(resource, CompletableFuture.completedFuture(resource)));
				continue;
			}

			Resource delegatedResource = delegatedGetResource(uri, loadOnDemand);
			if (delegatedResource != null) {
				futures.add(CompletableFuture.completedFuture(delegatedResource));
				continue;
			}

			if (loadOnDemand) {
				resource = demandCreateResource(uri);
				if (resource == null) {
					throw new RuntimeException(
							"Cannot create a resource for '" + uri + "'; a registered resource factory is needed");
				}

				futures.add(nowLoading.computeIfAbsent(resource, (r) -> loadingPool.submit(() -> {
					if (!r.isLoaded())
						super.demandLoadHelper(r);
					nowLoading.remove(r);
					return r;
				})));
			}
		}
		List<Resource> result = Lists.newArrayList();
		for (Future<Resource> future : futures) {
			try {
				result.add(future.get());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	@Override
	public Resource getResource(URI uri, boolean loadOnDemand) {
		Map<URI, Resource> map = getURIResourceMap();
		if (map == null || resourceLocator != null)
			return super.getResource(uri, loadOnDemand);
		Resource resource = map.get(uri);
		if (resource == null) {
			URI normalizedURI = getURIConverter().normalize(uri);
			resource = map.get(normalizedURI);
			if (resource != null) {
				normalizationMap.put(uri, normalizedURI);
			}
		}
		if (resource != null) {
			if (loadOnDemand && !resource.isLoaded()) {
				demandLoadHelper(resource);
			}
			try {
				return nowLoading.getOrDefault(resource, CompletableFuture.completedFuture(resource)).get();
			} catch(Exception e) {
				return resource;
			}
		}
		
	    Resource delegatedResource = delegatedGetResource(uri, loadOnDemand);
	    if (delegatedResource != null)
	    {
	      return delegatedResource;
	    }

	    if (loadOnDemand)
	    {
	      resource = demandCreateResource(uri);
	      if (resource == null) {
	        throw new RuntimeException("Cannot create a resource for '" + uri + "'; a registered resource factory is needed");
	      }

	      demandLoadHelper(resource);

	      try {
				return nowLoading.getOrDefault(resource, CompletableFuture.completedFuture(resource)).get();
			} catch(Exception e) {
				return resource;
			}
	    }

	    return null;
	}

	@Override
	protected void demandLoadHelper(Resource resource) {
		try {
			nowLoading.computeIfAbsent(resource, (r) -> loadingPool.submit(() -> {
				super.demandLoadHelper(r);
				return r;
			})).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException();
		}
	}

	@Override
	protected Resource demandCreateResource(URI uri) {
		writeLock.lock();
		try {
			Resource maybeCreated = getURIResourceMap().get(uri);
			if (maybeCreated != null) {
				return maybeCreated;
			}
			return super.demandCreateResource(uri);
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * @since 2.4
	 */
	@Override
	protected ResourcesList createResourceList() {
		return new ResourcesList() {

			private static final long serialVersionUID = -4217297874237993194L;

			@Override
			protected NotificationChain inverseAdd(Resource resource, NotificationChain notifications) {
				writeLock.lock();
				try {
					return super.inverseAdd(resource, notifications);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				readLock.lock();
				try {
					return super.containsAll(c);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public Resource set(int index, Resource object) {
				writeLock.lock();
				try {
					return super.set(index, object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean add(Resource object) {
				writeLock.lock();
				try {
					return super.add(object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public void add(int index, Resource object) {
				writeLock.lock();
				try {
					super.add(index, object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAll(Collection<? extends Resource> collection) {
				writeLock.lock();
				try {
					return super.addAll(collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAll(int index, Collection<? extends Resource> collection) {
				writeLock.lock();
				try {
					return super.addAll(index, collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean remove(Object object) {
				writeLock.lock();
				try {
					return super.remove(object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean retainAll(Collection<?> collection) {
				writeLock.lock();
				try {
					return super.retainAll(collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public void move(int index, Resource object) {
				writeLock.lock();
				try {
					super.move(index, object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean equals(Object object) {
				readLock.lock();
				try {
					return super.equals(object);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public int hashCode() {
				readLock.lock();
				try {
					return super.hashCode();
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public String toString() {
				readLock.lock();
				try {
					return super.toString();
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public Iterator<Resource> iterator() {
				return new SynchronizedEIterator();
			}

			class SynchronizedEIterator extends AbstractEList<Resource>.EIterator<Resource> {
				@Override
				public boolean hasNext() {
					readLock.lock();
					try {
						return super.hasNext();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource next() {
					readLock.lock();
					try {
						return super.next();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public void remove() {
					writeLock.lock();
					try {
						super.remove();
					} finally {
						writeLock.unlock();
					}
				}
			}

			class SynchronizedEListIterator extends AbstractEList<Resource>.EListIterator<Resource> {

				public SynchronizedEListIterator() {
					super();
				}

				public SynchronizedEListIterator(int index) {
					super(index);
				}

				@Override
				public void add(Resource object) {
					writeLock.lock();
					try {
						super.add(object);
					} finally {
						writeLock.unlock();
					}
				}

				@Override
				public boolean hasNext() {
					readLock.lock();
					try {
						return super.hasNext();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public boolean hasPrevious() {
					readLock.lock();
					try {
						return super.hasPrevious();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource next() {
					readLock.lock();
					try {
						return super.next();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource previous() {
					readLock.lock();
					try {
						return super.previous();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public int previousIndex() {
					readLock.lock();
					try {
						return super.previousIndex();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public void remove() {
					writeLock.lock();
					try {
						super.remove();
					} finally {
						writeLock.unlock();
					}
				}

				@Override
				public void set(Resource object) {
					writeLock.lock();
					try {
						super.set(object);
					} finally {
						writeLock.unlock();
					}
				}
			}

			@Override
			public ListIterator<Resource> listIterator() {
				return new SynchronizedEListIterator();
			}

			@Override
			public ListIterator<Resource> listIterator(int index) {
				readLock.lock();
				try {
					int size = size();
					if (index < 0 || index > size)
						throw new BasicIndexOutOfBoundsException(index, size);
					return new SynchronizedEListIterator(index);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public int indexOf(Object object) {
				readLock.lock();
				try {
					return super.indexOf(object);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public int lastIndexOf(Object object) {
				readLock.lock();
				try {
					return super.lastIndexOf(object);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public Object[] toArray() {
				readLock.lock();
				try {
					return super.toArray();
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public <T> T[] toArray(T[] array) {
				readLock.lock();
				try {
					return super.toArray(array);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public void setData(int size, Object[] data) {
				writeLock.lock();
				try {
					super.setData(size, data);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public Resource get(int index) {
				readLock.lock();
				try {
					return super.get(index);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public Resource basicGet(int index) {
				readLock.lock();
				try {
					return super.basicGet(index);
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public void shrink() {
				writeLock.lock();
				try {
					super.shrink();
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public void grow(int minimumCapacity) {
				writeLock.lock();
				try {
					super.grow(minimumCapacity);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public Object clone() {
				readLock.lock();
				try {
					return super.clone();
				} finally {
					readLock.unlock();
				}
			}

			@Override
			public void addUnique(Resource object) {
				writeLock.lock();
				try {
					super.addUnique(object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public void addUnique(int index, Resource object) {
				writeLock.lock();
				try {
					super.addUnique(index, object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAllUnique(Collection<? extends Resource> collection) {
				writeLock.lock();
				try {
					return super.addAllUnique(collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAllUnique(int index, Collection<? extends Resource> collection) {
				writeLock.lock();
				try {
					return super.addAllUnique(index, collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAllUnique(Object[] objects, int start, int end) {
				writeLock.lock();
				try {
					return super.addAllUnique(objects, start, end);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean addAllUnique(int index, Object[] objects, int start, int end) {
				writeLock.lock();
				try {
					return super.addAllUnique(index, objects, start, end);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public NotificationChain basicAdd(Resource object, NotificationChain notifications) {
				writeLock.lock();
				try {
					return super.basicAdd(object, notifications);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public Resource remove(int index) {
				writeLock.lock();
				try {
					return super.remove(index);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public boolean removeAll(Collection<?> collection) {
				writeLock.lock();
				try {
					return super.removeAll(collection);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public NotificationChain basicRemove(Object object, NotificationChain notifications) {
				writeLock.lock();
				try {
					return super.basicRemove(object, notifications);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public void clear() {
				writeLock.lock();
				try {
					super.clear();
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public Resource setUnique(int index, Resource object) {
				writeLock.lock();
				try {
					return super.setUnique(index, object);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public NotificationChain basicSet(int index, Resource object, NotificationChain notifications) {
				writeLock.lock();
				try {
					return super.basicSet(index, object, notifications);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public Resource move(int targetIndex, int sourceIndex) {
				writeLock.lock();
				try {
					return super.move(targetIndex, sourceIndex);
				} finally {
					writeLock.unlock();
				}
			}

			@Override
			public List<Resource> basicList() {
				return super.basicList();
			}

			@Override
			public Iterator<Resource> basicIterator() {
				return new SynchronizedNonResolvingEIterator();
			}

			@Override
			public ListIterator<Resource> basicListIterator() {
				return new SynchronizedNonResolvingEListIterator();
			}

			@Override
			public ListIterator<Resource> basicListIterator(int index) {
				readLock.lock();
				try {
					int size = size();
					if (index < 0 || index > size)
						throw new BasicIndexOutOfBoundsException(index, size);
					return new SynchronizedNonResolvingEListIterator(index);
				} finally {
					readLock.unlock();
				}
			}

			class SynchronizedNonResolvingEIterator extends AbstractEList<Resource>.NonResolvingEIterator<Resource> {
				@Override
				public boolean hasNext() {
					readLock.lock();
					try {
						return super.hasNext();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource next() {
					readLock.lock();
					try {
						return super.next();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public void remove() {
					writeLock.lock();
					try {
						super.remove();
					} finally {
						writeLock.unlock();
					}
				}
			}

			class SynchronizedNonResolvingEListIterator
					extends AbstractEList<Resource>.NonResolvingEListIterator<Resource> {

				public SynchronizedNonResolvingEListIterator() {
					super();
				}

				public SynchronizedNonResolvingEListIterator(int index) {
					super(index);
				}

				@Override
				public void add(Resource object) {
					writeLock.lock();
					try {
						super.add(object);
					} finally {
						writeLock.unlock();
					}
				}

				@Override
				public boolean hasNext() {
					readLock.lock();
					try {
						return super.hasNext();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public boolean hasPrevious() {
					readLock.lock();
					try {
						return super.hasPrevious();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource next() {
					readLock.lock();
					try {
						return super.next();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public Resource previous() {
					readLock.lock();
					try {
						return super.previous();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public int previousIndex() {
					readLock.lock();
					try {
						return super.previousIndex();
					} finally {
						readLock.unlock();
					}
				}

				@Override
				public void remove() {
					writeLock.lock();
					try {
						super.remove();
					} finally {
						writeLock.unlock();
					}
				}

				@Override
				public void set(Resource object) {
					writeLock.lock();
					try {
						super.set(object);
					} finally {
						writeLock.unlock();
					}
				}
			}

			@Override
			public boolean contains(Object object) {
				readLock.lock();
				try {
					return super.contains(object);
				} finally {
					readLock.unlock();
				}
			}

		};
	}

}
