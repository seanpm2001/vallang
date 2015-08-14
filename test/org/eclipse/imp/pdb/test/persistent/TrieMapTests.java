/*******************************************************************************
 * Copyright (c) 2014 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI  
 *******************************************************************************/
package org.eclipse.imp.pdb.test.persistent;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.Random;

import org.eclipse.imp.pdb.facts.util.TrieMap_5Bits;
import org.eclipse.imp.pdb.facts.util.TrieMap_Heterogeneous;
import org.junit.Test;

public class TrieMapTests {

	final static int size = (int) Math.pow(2, 10);

	@Test
	public void testPrintStatsSequential() {
		// int size = 128;

		TrieMap_5Bits<Integer, Integer> map = (TrieMap_5Bits) TrieMap_5Bits.of();

		for (int i = size; i > 0; i--) {
			TrieMap_5Bits<Integer, Integer> res = (TrieMap_5Bits) map.__put(i, i);
			assert res.containsKey(i);
			map = res;
		}

		map.printStatistics();
	}

	@Test
	public void testPrintStatsRandom() {
		// int size = 128;

		TrieMap_5Bits<Integer, Integer> map = (TrieMap_5Bits) TrieMap_5Bits.of();

		Random rand = new Random(13);

		for (int i = size; i > 0; i--) {
			final int j = rand.nextInt();

			TrieMap_5Bits<Integer, Integer> res = (TrieMap_5Bits) map.__put(j, j);
			assert res.containsKey(j);
			map = res;
		}

		map.printStatistics();
	}

	@Test
	public void testCheckPrefixConstruction() {
		// int size = 128;

		TrieMap_5Bits<Integer, Integer> map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits<Integer, Integer> res1 = (TrieMap_5Bits) map.__put(63, 63).__put(64, 64)
						.__put(32768, 32768).__put(2147483647, 2147483647).__put(65536, 65536);

		assert res1.containsKey(63);
		assert res1.containsKey(64);
		assert res1.containsKey(32768);
		assert res1.containsKey(65536);
		assert res1.containsKey(2147483647);

		TrieMap_5Bits<Integer, Integer> res2 = (TrieMap_5Bits) map.__put(2147483647, 2147483647)
						.__put(32768, 32768).__put(63, 63).__put(64, 64).__put(65536, 65536);

		assert res2.containsKey(63);
		assert res2.containsKey(64);
		assert res2.containsKey(32768);
		assert res2.containsKey(65536);
		assert res2.containsKey(2147483647);

		assert res1.equals(res2);

		map.printStatistics();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromBeginUponDelete() {

		TrieMap_5Bits<Integer, Integer> map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits<Integer, Integer> res1 = (TrieMap_5Bits) map.__put(1, 1).__put(2, 2);

		TrieMap_5Bits<Integer, Integer> res2 = (TrieMap_5Bits) res1.__put(32769, 32769).__remove(2);

		// what to test for?
		assert !res1.equals(res2);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromMiddleUponDelete() {

		TrieMap_5Bits<Integer, Integer> map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits<Integer, Integer> res1 = (TrieMap_5Bits) map.__put(1, 1).__put(2, 2)
						.__put(65, 65).__put(66, 66);

		TrieMap_5Bits<Integer, Integer> res2 = (TrieMap_5Bits) res1.__put(32769, 32769)
						.__remove(66);

		// what to test for?
		assert !res1.equals(res2);
	}

	public static PureSeparateHashCodeInteger p(int value, int hash) {
		return new PureSeparateHashCodeInteger(value, hash);
	}
	
	public static PureSeparateHashCodeInteger p(int value) {
		return new PureSeparateHashCodeInteger(value, value);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromBeginUponDelete_HashCollisionNode1() {

		TrieMap_5Bits map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits res1 = (TrieMap_5Bits) map.__put(p(11, 1), p(11, 1)).__put(p(12, 1), p(12, 1));
		assertTrue(res1.containsKey(p(11, 1)));
		assertTrue(res1.containsKey(p(12, 1)));
		
		TrieMap_5Bits res2 = (TrieMap_5Bits) res1.__remove(p(12, 1));
		assertTrue(res2.containsKey(p(11, 1)));
		assertEquals(TrieMap_5Bits.of(p(11, 1), p(11, 1)), res2);		
		
		TrieMap_5Bits res3 = (TrieMap_5Bits) res1.__remove(p(11, 1));
		assertTrue(res3.containsKey(p(12, 1)));
		assertEquals(TrieMap_5Bits.of(p(12, 1), p(12, 1)), res3);
		
		TrieMap_5Bits resX = (TrieMap_5Bits) res1.__put(p(32769), p(32769)).__remove(p(12, 1));
		assertTrue(resX.containsKey(p(11, 1)));
		assertTrue(resX.containsKey(p(32769)));

		// what to test for?
		assert !res1.equals(resX);
	}	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromBeginUponDelete_HashCollisionNode2() {

		TrieMap_5Bits map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits res1 = (TrieMap_5Bits) map.__put(p(32769_1, 32769), p(32769_1, 32769)).__put(p(32769_2, 32769), p(32769_2, 32769));
		assertEquals(2, res1.size());
		assertTrue(res1.containsKey(p(32769_1, 32769)));
		assertTrue(res1.containsKey(p(32769_2, 32769)));
		
		TrieMap_5Bits res2 = (TrieMap_5Bits) res1.__put(p(1, 1), p(1, 1));
		assertEquals(3, res2.size());
		assertTrue(res2.containsKey(p(1, 1)));
		assertTrue(res2.containsKey(p(32769_1, 32769)));
		assertTrue(res2.containsKey(p(32769_2, 32769)));		
		
		TrieMap_5Bits res3 = (TrieMap_5Bits) res2.__remove(p(32769_2, 32769));
		assertEquals(2, res3.size());
		assertTrue(res3.containsKey(p(1, 1)));
		assertTrue(res3.containsKey(p(32769_1, 32769)));
		
		TrieMap_5Bits expected = (TrieMap_5Bits) TrieMap_5Bits.of(p(1, 1), p(1, 1), p(32769_1, 32769), p(32769_1, 32769));
		assertEquals(expected, res3);
	}	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromBeginUponDelete_HashCollisionNode3() {

		TrieMap_5Bits map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits res1 = (TrieMap_5Bits) map.__put(p(32769_1, 32769), p(32769_1, 32769)).__put(p(32769_2, 32769), p(32769_2, 32769));
		assertEquals(2, res1.size());
		assertTrue(res1.containsKey(p(32769_1, 32769)));
		assertTrue(res1.containsKey(p(32769_2, 32769)));
		
		TrieMap_5Bits res2 = (TrieMap_5Bits) res1.__put(p(1, 1), p(1, 1));
		assertEquals(3, res2.size());
		assertTrue(res2.containsKey(p(1, 1)));
		assertTrue(res2.containsKey(p(32769_1, 32769)));
		assertTrue(res2.containsKey(p(32769_2, 32769)));		
		
		TrieMap_5Bits res3 = (TrieMap_5Bits) res2.__remove(p(1, 1));
		assertEquals(2, res3.size());
		assertTrue(res3.containsKey(p(32769_1, 32769)));
		assertTrue(res3.containsKey(p(32769_2, 32769)));
		
		assertEquals(res1, res3);
	}	

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCheckCompactionFromBeginUponDelete_HashCollisionNode4() {

		TrieMap_5Bits map = (TrieMap_5Bits) TrieMap_5Bits.of();

		TrieMap_5Bits res1 = (TrieMap_5Bits) map.__put(p(32769_1, 32769), p(32769_1, 32769)).__put(p(32769_2, 32769), p(32769_2, 32769));
		assertEquals(2, res1.size());
		assertTrue(res1.containsKey(p(32769_1, 32769)));
		assertTrue(res1.containsKey(p(32769_2, 32769)));
		
		TrieMap_5Bits res2 = (TrieMap_5Bits) res1.__put(p(5), p(5));
		assertEquals(3, res2.size());
		assertTrue(res2.containsKey(p(5)));
		assertTrue(res2.containsKey(p(32769_1, 32769)));
		assertTrue(res2.containsKey(p(32769_2, 32769)));		
		
		TrieMap_5Bits res3 = (TrieMap_5Bits) res2.__remove(p(5));
		assertEquals(2, res3.size());
		assertTrue(res3.containsKey(p(32769_1, 32769)));
		assertTrue(res3.containsKey(p(32769_2, 32769)));
		
		assertEquals(res1, res3);
	}	
	
	
	@Test
	public void testRecoverMask() {
		byte mask = recoverMask(-2147483648, (byte) 1);
		assertTrue(mask == 31);
	}

	static byte recoverMask(int map, byte i_th) {
		assert 1 <= i_th && i_th <= 32;

		byte cnt1 = 0;
		byte mask = 0;

		while (mask < 32) {
			if ((map & 0x01) == 0x01) {
				cnt1 += 1;

				if (cnt1 == i_th) {
					return mask;
				}
			}

			map = map >> 1;
			mask += 1;
		}

		throw new RuntimeException("Called with invalid arguments."); // cnt1 !=
																		// i_th
	}

	@Test
	public void testPrintStatsRandomSmallAndBigIntegers() {
		TrieMap_Heterogeneous map = (TrieMap_Heterogeneous) TrieMap_Heterogeneous.of();
		long smallCount = 0;
		long bigCount = 0;
				
		Random rand = new Random(13);
		
		for (int i = size; i > 0; i--) {
			final int j = rand.nextInt();
			// System.out.println(j);
			
			final BigInteger bigJ = BigInteger.valueOf(j).multiply(BigInteger.valueOf(j));
			// System.out.println(bigJ);
			
			if (i % 20 == 0) { // earlier: bigJ.bitLength() > 31
				// System.out.println("BIG");
				bigCount++;
				TrieMap_Heterogeneous res = (TrieMap_Heterogeneous) map.__put(bigJ, bigJ);
				assert res.containsKey(bigJ);
				map = res;
			} else {
				// System.out.println("SMALL");
				smallCount++;
				TrieMap_Heterogeneous res = (TrieMap_Heterogeneous) map.__put(j, j);
				assert res.containsKey(j);
				map = res;
			}
		}

		// map.printStatistics();
		// System.out.println(map);
		
		System.out.println();		
		System.out.println(String.format("PRIMITIVE:   %10d (%.2f percent)", smallCount, 100. * smallCount / (smallCount + bigCount)));
		System.out.println(String.format("BIG_INTEGER: %10d (%.2f percent)", bigCount, 100. * bigCount / (smallCount + bigCount)));
		System.out.println(String.format("UNIQUE:      %10d (%.2f percent)", map.size(), 100. * map.size() / (smallCount + bigCount)));
		System.out.println();
	}

}
