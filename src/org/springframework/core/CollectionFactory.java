/*
 * Copyright 2002-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.ClassUtils;

/**
 * Factory for collections, being aware of Commons Collection 3.x's extended
 * collections as well as of JDK 1.5+ concurrent collections and backport-concurrent
 * collections. Mainly for internal use within the framework.
 *
 * <p>The goal of this class is to avoid runtime dependencies on JDK 1.5+ and
 * Commons Collections 3.x, simply using the best collection implementation
 * that is available at runtime. As of Spring 2.5, JDK 1.4 is required,
 * so former adapter methods for JDK 1.3/1.4 always return the JDK 1.4
 * collections now. The adapter methods are still kept for supporting
 * Spring-based applications/frameworks which were built to support JDK 1.3.
 *
 * @author Juergen Hoeller
 * @since 1.1.1
 */
public abstract class CollectionFactory {

	private static final Log logger = LogFactory.getLog(CollectionFactory.class);

	/** Whether the Commons Collections 3.x library is present on the classpath */
	private static final boolean commonsCollections3Available =
			ClassUtils.isPresent("org.apache.commons.collections.map.CaseInsensitiveMap",
					CollectionFactory.class.getClassLoader());

	/** Whether the backport-concurrent library is present on the classpath */
	private static final boolean backportConcurrentAvailable =
			ClassUtils.isPresent("edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap",
					CollectionFactory.class.getClassLoader());


	private static Class navigableSetClass = null;

	private static Class navigableMapClass = null;

	private static final Set approximableCollectionTypes = new HashSet(10);

	private static final Set approximableMapTypes = new HashSet(6);

	static {
		// Standard collection interfaces
		approximableCollectionTypes.add(Collection.class);
		approximableCollectionTypes.add(List.class);
		approximableCollectionTypes.add(Set.class);
		approximableCollectionTypes.add(SortedSet.class);
		approximableMapTypes.add(Map.class);
		approximableMapTypes.add(SortedMap.class);

		// New Java 6 collection interfaces
		ClassLoader cl = CollectionFactory.class.getClassLoader();
		try {
			navigableSetClass = cl.loadClass("java.util.NavigableSet");
			navigableMapClass = cl.loadClass("java.util.NavigableMap");
			approximableCollectionTypes.add(navigableSetClass);
			approximableMapTypes.add(navigableMapClass);
		}
		catch (ClassNotFoundException ex) {
			// not running on Java 6 or above...
		}

		// Common concrete collection classes
		approximableCollectionTypes.add(ArrayList.class);
		approximableCollectionTypes.add(LinkedList.class);
		approximableCollectionTypes.add(HashSet.class);
		approximableCollectionTypes.add(LinkedHashSet.class);
		approximableCollectionTypes.add(TreeSet.class);
		approximableMapTypes.add(HashMap.class);
		approximableMapTypes.add(LinkedHashMap.class);
		approximableMapTypes.add(TreeMap.class);
	}

	/**
	 * Create a linked Set if possible: This implementation always
	 * creates a {@link java.util.LinkedHashSet}, since Spring 2.5
	 * requires JDK 1.4 anyway.
	 * @param initialCapacity the initial capacity of the Set
	 * @return the new Set instance
	 * @deprecated as of Spring 2.5, for usage on JDK 1.4 or higher
	 */
	public static Set createLinkedSetIfPossible(int initialCapacity) {
		return new LinkedHashSet(initialCapacity);
	}

	/**
	 * Create a copy-on-write Set (allowing for synchronization-less iteration),
	 * requiring JDK >= 1.5 or the backport-concurrent library on the classpath.
	 * Prefers a JDK 1.5+ CopyOnWriteArraySet to its backport-concurrent equivalent.
	 * Throws an IllegalStateException if no copy-on-write Set is available.
	 * @return the new Set instance
	 * @throws IllegalStateException if no copy-on-write Set is available
	 * @see java.util.concurrent.ConcurrentHashMap
	 * @see edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap
	 */
	public static Set createCopyOnWriteSet() {
		if (JdkVersion.isAtLeastJava15()) {
			logger.trace("Creating [java.util.concurrent.CopyOnWriteArraySet]");
			return JdkConcurrentCollectionFactory.createCopyOnWriteArraySet();
		}
		else if (backportConcurrentAvailable) {
			logger.trace("Creating [edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArraySet]");
			return BackportConcurrentCollectionFactory.createCopyOnWriteArraySet();
		}
		else {
			throw new IllegalStateException("Cannot create CopyOnWriteArraySet - " +
					"neither JDK 1.5 nor backport-concurrent available on the classpath");
		}
	}

	/**
	 * Create a linked Map if possible: This implementation always
	 * creates a {@link java.util.LinkedHashMap}, since Spring 2.5
	 * requires JDK 1.4 anyway.
	 * @param initialCapacity the initial capacity of the Map
	 * @return the new Map instance
	 * @deprecated as of Spring 2.5, for usage on JDK 1.4 or higher
	 */
	public static Map createLinkedMapIfPossible(int initialCapacity) {
		return new LinkedHashMap(initialCapacity);
	}

	/**
	 * Create a linked case-insensitive Map if possible: if Commons Collections
	 * 3.x is available, a CaseInsensitiveMap with ListOrderedMap decorator will
	 * be created. Else, a JDK {@link java.util.LinkedHashMap} will be used.
	 * @param initialCapacity the initial capacity of the Map
	 * @return the new Map instance
	 * @see org.apache.commons.collections.map.CaseInsensitiveMap
	 * @see org.apache.commons.collections.map.ListOrderedMap
	 */
	public static Map createLinkedCaseInsensitiveMapIfPossible(int initialCapacity) {
		if (commonsCollections3Available) {
			logger.trace("Creating [org.apache.commons.collections.map.ListOrderedMap/CaseInsensitiveMap]");
			return CommonsCollectionFactory.createListOrderedCaseInsensitiveMap(initialCapacity);
		}
		else {
			logger.debug("Falling back to [java.util.LinkedHashMap] for linked case-insensitive map");
			return new LinkedHashMap(initialCapacity);
		}
	}

	/**
	 * Create an identity Map if possible: This implementation always
	 * creates a {@link java.util.IdentityHashMap}, since Spring 2.5
	 * requires JDK 1.4 anyway.
	 * @param initialCapacity the initial capacity of the Map
	 * @return the new Map instance
	 * @deprecated as of Spring 2.5, for usage on JDK 1.4 or higher
	 */
	public static Map createIdentityMapIfPossible(int initialCapacity) {
		return new IdentityHashMap(initialCapacity);
	}

	/**
	 * Create a concurrent Map if possible: that is, if running on JDK >= 1.5
	 * or if the backport-concurrent library is available. Prefers a JDK 1.5+
	 * ConcurrentHashMap to its backport-concurrent equivalent. Falls back
	 * to a plain synchronized HashMap if no concurrent Map is available.
	 * @param initialCapacity the initial capacity of the Map
	 * @return the new Map instance
	 * @see java.util.concurrent.ConcurrentHashMap
	 * @see edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap
	 */
	public static Map createConcurrentMapIfPossible(int initialCapacity) {
		if (JdkVersion.isAtLeastJava15()) {
			logger.trace("Creating [java.util.concurrent.ConcurrentHashMap]");
			return JdkConcurrentCollectionFactory.createConcurrentHashMap(initialCapacity);
		}
		else if (backportConcurrentAvailable) {
			logger.trace("Creating [edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap]");
			return BackportConcurrentCollectionFactory.createConcurrentHashMap(initialCapacity);
		}
		else {
			logger.debug("Falling back to plain synchronized [java.util.HashMap] for concurrent map");
			return Collections.synchronizedMap(new HashMap(initialCapacity));
		}
	}

	/**
	 * Create a concurrent Map with a dedicated {@link ConcurrentMap} interface,
	 * requiring JDK >= 1.5 or the backport-concurrent library on the classpath.
	 * Prefers a JDK 1.5+ ConcurrentHashMap to its backport-concurrent equivalent.
	 * Throws an IllegalStateException if no concurrent Map is available.
	 * @param initialCapacity the initial capacity of the Map
	 * @return the new ConcurrentMap instance
	 * @throws IllegalStateException if no concurrent Map is available
	 * @see java.util.concurrent.ConcurrentHashMap
	 * @see edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap
	 */
	public static ConcurrentMap createConcurrentMap(int initialCapacity) {
		if (JdkVersion.isAtLeastJava15()) {
			logger.trace("Creating [java.util.concurrent.ConcurrentHashMap]");
			return new JdkConcurrentHashMap(initialCapacity);
		}
		else if (backportConcurrentAvailable) {
			logger.trace("Creating [edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap]");
			return new BackportConcurrentHashMap(initialCapacity);
		}
		else {
			throw new IllegalStateException("Cannot create ConcurrentHashMap - " +
					"neither JDK 1.5 nor backport-concurrent available on the classpath");
		}
	}


	/**
	 * Determine whether the given collection type is an approximable type,
	 * i.e. a type that {@link #createApproximateCollection} can approximate.
	 * @param collectionType the collection type to check
	 * @return <code>true</code> if the type is approximable,
	 * <code>false</code> if it is not
	 */
	public static boolean isApproximableCollectionType(Class collectionType) {
		return (collectionType != null && approximableCollectionTypes.contains(collectionType));
	}

	/**
	 * Create the most approximate collection for the given collection.
	 * <p>Creates an ArrayList, TreeSet or linked Set for a List, SortedSet
	 * or Set, respectively.
	 * @param collection the original collection object
	 * @param initialCapacity the initial capacity
	 * @return the new collection instance
	 * @see java.util.ArrayList
	 * @see java.util.TreeSet
	 * @see java.util.LinkedHashSet
	 */
	public static Collection createApproximateCollection(Object collection, int initialCapacity) {
		if (collection instanceof LinkedList) {
			return new LinkedList();
		}
		else if (collection instanceof List) {
			return new ArrayList(initialCapacity);
		}
		else if (collection instanceof SortedSet) {
			return new TreeSet(((SortedSet) collection).comparator());
		}
		else {
			return new LinkedHashSet(initialCapacity);
		}
	}

	/**
	 * Create the most appropriate collection for the given collection type.
	 * <p>Creates an ArrayList, TreeSet or linked Set for a List, SortedSet
	 * or Set, respectively.
	 * @param collectionType the desired type of the target Collection
	 * @param initialCapacity the initial capacity
	 * @return the new Collection instance
	 * @see java.util.ArrayList
	 * @see java.util.TreeSet
	 * @see java.util.LinkedHashSet
	 */
	public static Collection createCollection(Class collectionType, int initialCapacity) {
		if (collectionType.isInterface()) {
			if (List.class.equals(collectionType)) {
				return new ArrayList(initialCapacity);
			}
			else if (SortedSet.class.equals(collectionType) || collectionType.equals(navigableSetClass)) {
				return new TreeSet();
			}
			else if (Set.class.equals(collectionType) || Collection.class.equals(collectionType)) {
				return new LinkedHashSet(initialCapacity);
			}
			else {
				throw new IllegalArgumentException("Unsupported Collection interface: " + collectionType.getName());
			}
		}
		else {
			if (!Collection.class.isAssignableFrom(collectionType)) {
				throw new IllegalArgumentException("Unsupported Collection type: " + collectionType.getName());
			}
			try {
				return (Collection) collectionType.newInstance();
			}
			catch (Exception ex) {
				throw new IllegalArgumentException("Could not instantiate Collection type: " + collectionType.getName());
			}
		}
	}

	/**
	 * Determine whether the given map type is an approximable type,
	 * i.e. a type that {@link #createApproximateMap} can approximate.
	 * @param mapType the map type to check
	 * @return <code>true</code> if the type is approximable,
	 * <code>false</code> if it is not
	 */
	public static boolean isApproximableMapType(Class mapType) {
		return (mapType != null && approximableMapTypes.contains(mapType));
	}

	/**
	 * Create the most approximate map for the given map.
	 * <p>Creates a TreeMap or linked Map for a SortedMap or Map, respectively.
	 * @param map the original map object
	 * @param initialCapacity the initial capacity
	 * @return the new collection instance
	 * @see java.util.TreeMap
	 * @see java.util.LinkedHashMap
	 */
	public static Map createApproximateMap(Object map, int initialCapacity) {
		if (map instanceof SortedMap) {
			return new TreeMap(((SortedMap) map).comparator());
		}
		else {
			return new LinkedHashMap(initialCapacity);
		}
	}


	/**
	 * Actual creation of Commons Collections.
	 * In separate inner class to avoid runtime dependency on Commons Collections 3.x.
	 */
	private static abstract class CommonsCollectionFactory {

		private static Map createListOrderedCaseInsensitiveMap(int initialCapacity) {
			// Commons Collections does not support initial capacity of 0.
			return ListOrderedMap.decorate(new CaseInsensitiveMap(initialCapacity == 0 ? 1 : initialCapacity));
		}
	}


	/**
	 * Actual creation of JDK 1.5+ concurrent Collections.
	 * In separate inner class to avoid runtime dependency on JDK 1.5.
	 */
	private static abstract class JdkConcurrentCollectionFactory {

		private static Set createCopyOnWriteArraySet() {
			return new CopyOnWriteArraySet();
		}

		private static Map createConcurrentHashMap(int initialCapacity) {
			return new ConcurrentHashMap(initialCapacity);
		}
	}


	/**
	 * Actual creation of backport-concurrent Collections.
	 * In separate inner class to avoid runtime dependency on the backport-concurrent library.
	 */
	private static abstract class BackportConcurrentCollectionFactory {

		private static Set createCopyOnWriteArraySet() {
			return new edu.emory.mathcs.backport.java.util.concurrent.CopyOnWriteArraySet();
		}

		private static Map createConcurrentHashMap(int initialCapacity) {
			return new edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap(initialCapacity);
		}
	}


	/**
	 * ConcurrentMap adapter for the JDK ConcurrentHashMap class.
	 */
	private static class JdkConcurrentHashMap extends ConcurrentHashMap implements ConcurrentMap {

		public JdkConcurrentHashMap(int initialCapacity) {
			super(initialCapacity);
		}
	}


	/**
	 * ConcurrentMap adapter for the backport-concurrent ConcurrentHashMap class.
	 */
	private static class BackportConcurrentHashMap
			extends edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap
			implements ConcurrentMap {

		public BackportConcurrentHashMap(int initialCapacity) {
			super(initialCapacity);
		}
	}

}
