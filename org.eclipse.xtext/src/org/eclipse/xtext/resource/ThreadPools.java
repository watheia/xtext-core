package org.eclipse.xtext.resource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.inject.Singleton;

@Singleton
public class ThreadPools {

	public final ExecutorService loadingPool = Executors.newFixedThreadPool(3);
	public final ExecutorService linkingPool = Executors.newFixedThreadPool(5);
	
	public void stop() throws Exception {
		loadingPool.shutdownNow();
		loadingPool.awaitTermination(10, TimeUnit.SECONDS);
		linkingPool.shutdownNow();
		linkingPool.awaitTermination(10, TimeUnit.SECONDS);
	}
	
}