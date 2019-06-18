/*******************************************************************************
* Copyright (c) 2007 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation

*******************************************************************************/

package io.usethesource.vallang.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISetWriter;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.Setup;
import io.usethesource.vallang.exceptions.FactTypeUseException;
import io.usethesource.vallang.type.TypeFactory;

@RunWith(Parameterized.class)
public final class SetSmokeTest {

  @Parameterized.Parameters
  public static Iterable<? extends Object> data() {
    return Setup.valueFactories();
  }

  private final IValueFactory vf;

  public SetSmokeTest(final IValueFactory vf) {
    this.vf = vf;
  }

  private TypeFactory tf;
  private IValue[] integers;
  private IValue[] doubles;
  private ISet integerUniverse;

  @Before
  public void setUp() throws Exception {
    this.tf = TypeFactory.getInstance();

    integers = new IValue[100];
    for (int i = 0; i < integers.length; i++) {
      integers[i] = vf.integer(i);
    }

    doubles = new IValue[100];
    for (int i = 0; i < doubles.length; i++) {
      doubles[i] = vf.real(i);
    }

    ISetWriter w = vf.setWriter();

    try {
      for (IValue v : integers) {
        w.insert(v);
      }

      integerUniverse = w.done();
    } catch (FactTypeUseException e) {
      fail("this should be type correct");
    }
  }

  @Test
  public void testInsert() {
    ISet set1 = vf.set();
    ISet set2;

    try {
      set2 = set1.insert(integers[0]);

      if (set2.size() != 1) {
        fail("insertion failed");
      }

      if (!set2.contains(integers[0])) {
        fail("insertion failed");
      }

    } catch (FactTypeUseException e1) {
      fail("type checking error:" + e1);
    }

    ISetWriter numberSet = vf.setWriter();

    try {
      numberSet.insert(integers[0]);
      numberSet.insert(doubles[0]);
    } catch (FactTypeUseException e) {
      fail("should be able to insert subtypes:" + e);
    }
  }

  @Test
  public void testEmpty() {
    ISet emptySet = vf.set();
    if (!emptySet.isEmpty()) {
      fail("empty set is not empty?");
    }

    if (!emptySet.getType().isRelation()) {
      fail("empty set should have relation type (yes really!)");
    }
  }

  @Test
  public void testContains() {
    ISet set1 = vf.set(integers[0], integers[1]);

    try {
      set1.contains(integers[0]);
    } catch (FactTypeUseException e) {
      fail("should be able to check for containment of integers");
    }
  }

  @Test
  public void testIntersect() {
    ISet set1 = vf.set();
    ISet set2 = vf.set();
    ISet set3 = vf.set(integers[0], integers[1], integers[2]);
    ISet set4 = vf.set(integers[2], integers[3], integers[4]);
    ISet set5 = vf.set(integers[3], integers[4], integers[5]);

    try {
      if (!set1.intersect(set2).isEmpty()) {
        fail("intersect of empty sets");
      }

      if (!set1.intersect(set3).isEmpty()) {
        fail("intersect with empty set");
      }

      if (!set3.intersect(set1).isEmpty()) {
        fail("insersect with empty set");
      }

      if (set3.intersect(set4).size() != 1) {
        fail("insersect failed");
      }

      if (!set4.intersect(set3).contains(integers[2])) {
        fail("intersect failed");
      }

      if (set4.intersect(set5).size() != 2) {
        fail("insersect failed");
      }

      if (!set5.intersect(set4).contains(integers[3])
          || !set5.intersect(set4).contains(integers[4])) {
        fail("intersect failed");
      }

      if (!set5.intersect(set3).isEmpty()) {
        fail("non-intersection sets");
      }

    } catch (FactTypeUseException et) {
      fail("this shouls all be typesafe");
    }
  }

  @Test
  public void testIsEmpty() {
    if (integerUniverse.isEmpty()) {
      fail("an empty universe is not so cosy");
    }

    if (!vf.set().isEmpty()) {
      fail("what's in an empty set?");
    }
  }

  @Test
  public void testSize() {
    if (vf.set().size() != 0) {
      fail("empty sets have size 0");
    }

    if (vf.set(integers[0]).size() != 1) {
      fail("singleton set should have size 1");
    }

    if (integerUniverse.size() != integers.length) {
      fail("weird size of universe");
    }
  }

  @Test
  public void testSubtract() {
    ISet set1 = vf.set();
    ISet set2 = vf.set();
    ISet set3 = vf.set(integers[0], integers[1], integers[2]);
    ISet set4 = vf.set(integers[2], integers[3], integers[4]);
    ISet set5 = vf.set(integers[3], integers[4], integers[5]);

    try {
      if (!set1.subtract(set2).isEmpty()) {
        fail("subtract of empty sets");
      }

      if (!set1.subtract(set3).isEmpty()) {
        fail("subtract with empty set");
      }

      if (!set3.subtract(set1).isEqual(set3)) {
        fail("subtract with empty set");
      }

      if (!set1.subtract(set3).isEqual(set1)) {
        fail("subtract with empty set");
      }

      if (set3.subtract(set4).size() != 2) {
        fail("subtract failed");
      }

      if (set4.subtract(set3).contains(integers[2])) {
        fail("subtract failed");
      }

      if (set4.subtract(set5).size() != 1) {
        fail("insersect failed");
      }

      if (set5.subtract(set4).contains(integers[3]) || set5.subtract(set4).contains(integers[4])) {
        fail("subtract failed");
      }

    } catch (FactTypeUseException et) {
      fail("this shouls all be typesafe");
    }

  }

  @Test
  public void testUnion() {
    ISet set1 = vf.set();
    ISet set2 = vf.set();
    ISet set3 = vf.set(integers[0], integers[1], integers[2]);
    ISet set4 = vf.set(integers[2], integers[3], integers[4]);
    ISet set5 = vf.set(integers[3], integers[4], integers[5]);

    try {
      if (!set1.union(set2).isEmpty()) {
        fail("union of empty sets");
      }

      if (!set1.union(set3).isEqual(set3)) {
        fail("union with empty set");
      }

      if (!set3.union(set1).isEqual(set3)) {
        fail("union with empty set");
      }

      if (!set1.union(set3).isEqual(set3)) {
        fail("union with empty set");
      }

      if (set3.union(set4).size() != 5) {
        fail("union failed");
      }

      if (!set4.union(set3).contains(integers[0]) || !set4.union(set3).contains(integers[1])
          || !set4.union(set3).contains(integers[2]) || !set4.union(set3).contains(integers[3])
          || !set4.union(set3).contains(integers[4])) {
        fail("union failed");
      }

      if (set4.union(set5).size() != 4) {
        fail("union failed");
      }

    } catch (FactTypeUseException et) {
      fail("this shouls all be typesafe");
    }

  }

  @Test
  public void testIterator() {
    try {
      Iterator<IValue> it = integerUniverse.iterator();
      int i;
      for (i = 0; it.hasNext(); i++) {
        if (!integerUniverse.contains(it.next())) {
          fail("iterator produces something weird");
        }
      }
      if (i != integerUniverse.size()) {
        fail("iterator did not iterate over everything");
      }
    } catch (FactTypeUseException e) {
      fail("should be type correct");
    }
  }

  @Test
  public void testGetElementType() {
    if (!integerUniverse.getElementType().isInteger()) {
      fail("elementType is broken");
    }
  }

  @Test
  public void testProductISet() {
    ISet test = vf.set(integers[0], integers[1], integers[2], integers[3]);
    ISet prod = test.product(test);

    if (prod.asRelation().arity() != 2) {
      fail("product's arity should be 2");
    }

    if (prod.size() != test.size() * test.size()) {
      fail("product's size should be square of size");
    }

  }

  @Test
  public void testProductIRelation() {
    ISet test = vf.set(integers[0], integers[1], integers[2], integers[3]);
    ISet prod = test.product(test);
    ISet prod2 = test.product(prod);

    if (prod2.asRelation().arity() != 2) {
      fail("product's arity should be 2");
    }

    if (prod2.size() != test.size() * prod.size()) {
      fail("product's size should be multiplication of arguments' sizes");
    }

  }

  @Test
  public void testTypeDoubleInsertOneRemoveWithSet() {
    ISet set1 = vf.set().insert(doubles[0]).insert(integers[0]).insert(integers[0]);
    ISet set2 = set1.delete(integers[0]);

    assertEquals(tf.realType(), set2.getElementType());
  }

  @Test
  public void testTypeDoubleInsertOneRemoveWithSetWriter() {
    ISetWriter w = vf.setWriter();
    w.insert(doubles[0]);
    w.insert(integers[0]);
    w.insert(integers[0]);
    ISet set1 = w.done();
    ISet set2 = set1.delete(integers[0]);

    assertEquals(tf.realType(), set2.getElementType());
  }

}
