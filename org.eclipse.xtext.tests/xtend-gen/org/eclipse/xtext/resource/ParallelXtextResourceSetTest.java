/**
 * Copyright (c) 2012, 2018 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.xtext.resource;

import org.eclipse.xtext.resource.ParallelResourceSet;
import org.eclipse.xtext.resource.SynchronizedXtextResourceSetTest;
import org.eclipse.xtext.resource.ThreadPools;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.xbase.lib.Exceptions;
import org.junit.After;
import org.junit.Before;

@SuppressWarnings("all")
public class ParallelXtextResourceSetTest extends SynchronizedXtextResourceSetTest {
  private ThreadPools pools;
  
  @Before
  public void initPools() {
    ThreadPools _threadPools = new ThreadPools();
    this.pools = _threadPools;
  }
  
  @After
  public void discardPools() {
    try {
      this.pools.stop();
      this.pools = null;
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  @Override
  protected XtextResourceSet createEmptyResourceSet() {
    return new ParallelResourceSet(this.pools);
  }
}
