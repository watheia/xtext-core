/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.resource;

import java.io.File;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xml.type.XMLTypePackage;
import org.eclipse.emf.mwe.utils.StandaloneSetup;
import org.eclipse.xtext.testing.GlobalRegistries;
import org.eclipse.xtext.testing.GlobalRegistries.GlobalStateMemento;
import org.eclipse.xtext.tests.TemporaryFolder;
import org.eclipse.xtext.util.concurrent.AbstractReadWriteAcces;
import org.eclipse.xtext.util.concurrent.IReadAccess;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.Lists;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
public class ConcurrentAccessTest extends Assert {

	private static final int RESOURCES = 1000;
	
	private static final int EXPECTATION = RESOURCES + 1;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	
	private Resource resource;
	
	private ThreadPools pools;

	private GlobalStateMemento globalStateMemento;

	static {
		new StandaloneSetup();
	}

	@Before
	public void setUp() throws Exception {
		globalStateMemento = GlobalRegistries.makeCopyOfGlobalState();
		EPackage.Registry.INSTANCE.put(XMLTypePackage.eNS_URI, XMLTypePackage.eINSTANCE);
		ResourceSet resourceSet = new ResourceSetImpl();
		resource = new XtextResource(URI.createFileURI("something.ecore"));
		resourceSet.getResources().add(resource);
		EPackage start = EcoreFactory.eINSTANCE.createEPackage();
		resource.getContents().add(start);
		for (int i = 0; i < RESOURCES; i++) {
			File tempFile = temporaryFolder.createTempFile("Package" + i, ".ecore");
			URI fileURI = URI.createFileURI(tempFile.getAbsolutePath());
			Resource toBeProxified = resourceSet.createResource(fileURI);
			EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
			ePackage.setNsURI("http://www.test.me/" + i);
			toBeProxified.getContents().add(ePackage);
			for (int j = 0; j < RESOURCES; j++) {
				EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
				annotation.setSource("Source" + j);
				start.getEAnnotations().add(annotation);
				EClass superClass = EcoreFactory.eINSTANCE.createEClass();
				superClass.setName("SuperClass" + j);
				ePackage.getEClassifiers().add(superClass);
				annotation.getReferences().add(superClass);
			}
			toBeProxified.save(null);
		}
		EcoreUtil.resolveAll(resourceSet);
		for (int i = RESOURCES; i >= 1; i--) {
			Resource toBeProxified = resourceSet.getResources().get(i);
			toBeProxified.unload();
			resourceSet.getResources().remove(toBeProxified);
		}
		pools = new ThreadPools();
	}

	@After
	public void tearDown() throws Exception {
		resource = null;
		pools.stop();
		pools = null;
		globalStateMemento.restoreGlobalState();
	}
	
	@Test public void testDummy() {
		assertEquals(1, resource.getResourceSet().getResources().size());
		EcoreUtil.resolveAll(resource);
		assertEquals(EXPECTATION, resource.getResourceSet().getResources().size());
	}
	
	@Test public void testResolveSingleThreaded() {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResources().add(resource);
		assertEquals(1, resourceSet.getResources().size());
		EPackage pack = (EPackage) resource.getContents().get(0);
		doResolveAllReferences(pack);
		assertEquals(EXPECTATION, resourceSet.getResources().size());
	}
	
	@Ignore @Test public void testMultiThreaded() throws InterruptedException {
		ResourceSet resourceSet = new XtextResourceSet();
		resourceSet.getResources().add(resource);
		boolean wasOk = resolveAllReferencesMultithreaded((EPackage) resource.getContents().get(0));
		if (wasOk)
			assertFalse(EXPECTATION == resourceSet.getResources().size());
		assertFalse("unresolvedProxy", wasOk);
	}
	
	@Test public void testMultiThreadedSynchronized() throws InterruptedException {
		ResourceSet resourceSet = new SynchronizedXtextResourceSet();
		resourceSet.getResources().add(resource);
		boolean wasOk = resolveAllReferencesMultithreaded((EPackage) resource.getContents().get(0));
		assertEquals(EXPECTATION, resourceSet.getResources().size());
		assertTrue("unresolvedProxy", wasOk);
	}
	
	@Test public void testMultiThreadedParallel() throws InterruptedException {
		ResourceSet resourceSet = new ParallelResourceSet(pools);
		resourceSet.getResources().add(resource);
		boolean wasOk = resolveAllReferencesMultithreaded((EPackage) resource.getContents().get(0));
		assertEquals(EXPECTATION, resourceSet.getResources().size());
		assertTrue("unresolvedProxy", wasOk);
	}
	
	@Test public void testMultiThreadedParallelOptimized() throws InterruptedException {
		ParallelResourceSet resourceSet = new ParallelResourceSet(pools);
		resourceSet.getResources().add(resource);
		List<Resource> resourceList = Lists.newArrayList(resource, resource, resource);
		List<Boolean> result = resourceSet.processResources(resourceList, r->doResolveAllReferences((EPackage)r.getContents().get(0)));
		boolean wasOk = result.stream().allMatch(failed->!failed);
		assertEquals(EXPECTATION, resourceSet.getResources().size());
		assertTrue("unresolvedProxy", wasOk);
	}
	
	@Ignore @Test public void testMultiThreadedUnitOfWork() throws InterruptedException {
		ResourceSet resourceSet = new XtextResourceSet();
		resourceSet.getResources().add(resource);
		boolean wasOk = resolveAllReferencesStateAccess((EPackage) resource.getContents().get(0));
		if (wasOk)
			assertFalse(EXPECTATION == resourceSet.getResources().size());
		assertFalse("unresolvedProxy", wasOk);
	}
	
	@Test public void testMultiThreadedSynchronizedUnitOfWork() throws InterruptedException {
		ResourceSet resourceSet = new SynchronizedXtextResourceSet();
		resourceSet.getResources().add(resource);
		boolean wasOk = resolveAllReferencesStateAccess((EPackage) resource.getContents().get(0));
		assertEquals(EXPECTATION, resourceSet.getResources().size());
		assertTrue("unresolvedProxy or concurrent modification or no such element", wasOk);
	}
	
	@Ignore @Test public void testMultiThreadedListAccess() throws InterruptedException {
		XtextResourceSet resourceSet = new XtextResourceSet();
		final List<Resource> resources = resourceSet.getResources();
		List<List<Resource>> resourcesToAdd = Lists.newArrayList();
		for(int i = 0; i < 5; i++) {
			List<Resource> threadList = Lists.newArrayList();
			for(int j = 0; j < 500; j++) {
				threadList.add((new ResourceImpl(URI.createURI("file:/" + i + "_" + j + ".xmi"))));
			}
			resourcesToAdd.add(threadList);
		}
		List<Thread> threads = Lists.newArrayList();
		for (int i = 0; i < 5; i++) {
			final List<Resource> addUs = resourcesToAdd.get(i);
			threads.add(new Thread() {
				@Override
				public void run() {
					for(Resource addMe: addUs) {
						resources.add(addMe);
					}
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		assertEquals(2500, resources.size());
		assertEquals(2500, resourceSet.getURIResourceMap().size());
	}
	
	@Test public void testMultiThreadedSynchronizedListAccess() throws InterruptedException {
		XtextResourceSet resourceSet = new SynchronizedXtextResourceSet();
		final List<Resource> resources = resourceSet.getResources();
		List<List<Resource>> resourcesToAdd = Lists.newArrayList();
		for(int i = 0; i < 5; i++) {
			List<Resource> threadList = Lists.newArrayList();
			for(int j = 0; j < 500; j++) {
				threadList.add((new ResourceImpl(URI.createURI("file:/" + i + "_" + j + ".xmi"))));
			}
			resourcesToAdd.add(threadList);
		}
		List<Thread> threads = Lists.newArrayList();
		for (int i = 0; i < 5; i++) {
			final List<Resource> addUs = resourcesToAdd.get(i);
			threads.add(new Thread() {
				@Override
				public void run() {
					for(Resource addMe: addUs) {
						resources.add(addMe);
					}
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		assertEquals(2500, resources.size());
		assertEquals(2500, resourceSet.getURIResourceMap().size());
	}
	
	@Test public void testMultiThreadedParallelListAccess() throws InterruptedException {
		XtextResourceSet resourceSet = new ParallelResourceSet(pools);
		final List<Resource> resources = resourceSet.getResources();
		List<List<Resource>> resourcesToAdd = Lists.newArrayList();
		for(int i = 0; i < 5; i++) {
			List<Resource> threadList = Lists.newArrayList();
			for(int j = 0; j < 500; j++) {
				threadList.add((new ResourceImpl(URI.createURI("file:/" + i + "_" + j + ".xmi"))));
			}
			resourcesToAdd.add(threadList);
		}
		List<Thread> threads = Lists.newArrayList();
		for (int i = 0; i < 5; i++) {
			final List<Resource> addUs = resourcesToAdd.get(i);
			threads.add(new Thread() {
				@Override
				public void run() {
					for(Resource addMe: addUs) {
						resources.add(addMe);
					}
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		assertEquals(2500, resources.size());
		assertEquals(2500, resourceSet.getURIResourceMap().size());
	}
	
	@Test public void testMultiThreadedParallelListAccessOptimized() throws InterruptedException {
		ParallelResourceSet resourceSet = new ParallelResourceSet(pools);
		final List<Resource> resources = resourceSet.getResources();
		List<Resource> resourcesToAdd = Lists.newArrayList();
		for(int i = 0; i < 5; i++) {
			for(int j = 0; j < 500; j++) {
				resourcesToAdd.add((new ResourceImpl(URI.createURI("file:/" + i + "_" + j + ".xmi"))));
			}
		}
		resourceSet.processResources(resourcesToAdd, r->resources.add(r));
		
		assertEquals(2500, resources.size());
		assertEquals(2500, resourceSet.getURIResourceMap().size());
	}

	/**
	 * @return <code>true</code> if everything was ok.
	 */
	protected boolean resolveAllReferencesMultithreaded(final EPackage pack) throws InterruptedException {
		final AtomicBoolean wasExceptionOrProxy = new AtomicBoolean(false);
		List<Thread> threads = Lists.newArrayList();
		for (int i = 0; i < 3; i++) {
			threads.add(new Thread() {
				@Override
				public void run() {
					boolean failed = doResolveAllReferences(pack);
					if (failed)
						wasExceptionOrProxy.set(failed);
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		boolean result = !wasExceptionOrProxy.get();
		return result;
	}
	
	/**
	 * @return <code>true</code> if everything was ok.
	 */
	protected boolean resolveAllReferencesStateAccess(final EPackage pack) throws InterruptedException {
		final IReadAccess<EPackage> stateAccess = new AbstractReadWriteAcces<EPackage>() {
			@Override
			protected EPackage getState() {
				return pack;
			}
		};
		final AtomicBoolean wasExceptionOrProxy = new AtomicBoolean(false);
		List<Thread> threads = Lists.newArrayList();
		for (int i = 0; i < 3; i++) {
			threads.add(new Thread() {
				@Override
				public void run() {
					Boolean failed = stateAccess.readOnly(new IUnitOfWork<Boolean, EPackage>() {
						@Override
						public Boolean exec(EPackage state) throws Exception {
							return doResolveAllReferences(pack);
						}
					});
					if (failed == null || failed)
						wasExceptionOrProxy.set(true);
				}
			});
		}
		for (Thread thread : threads) {
			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}
		return !wasExceptionOrProxy.get();
	}

	protected boolean doResolveAllReferences(final EPackage pack) {
		boolean failed = false;
		try {
			for(EAnnotation annotation: pack.getEAnnotations()) {
				EList<EObject> references = annotation.getReferences();
				for(EObject reference: references) {
					if (reference == null) {
						failed = true;
						System.out.println("REFERENCE IS NULL");
					}
					else if (reference.eIsProxy()) {
						failed = true;
						System.out.println("REFERENCE IS PROXY");
					}
				}
			}
		} catch(ConcurrentModificationException e) {
			e.printStackTrace();
			failed = true;
		} catch(NoSuchElementException e) {
			e.printStackTrace();
			failed = true;
		}
		return failed;
	}

}
