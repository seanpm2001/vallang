/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation, 2009-2015 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
 *    Jurgen Vinju - initial API and implementation
 *******************************************************************************/

package org.rascalmpl.value.type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.rascalmpl.value.IConstructor;
import org.rascalmpl.value.IList;
import org.rascalmpl.value.ISetWriter;
import org.rascalmpl.value.IString;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.value.exceptions.FactTypeUseException;
import org.rascalmpl.value.exceptions.IllegalOperationException;
import org.rascalmpl.value.exceptions.TypeReificationException;

/**
 * This class is the abstract implementation for all types. Types are ordered in
 * a partially ordered type hierarchy with 'value' as the largest type and
 * 'void' as the smallest. Each type represents a set of values.
 * <p>
 * Users of this API will generally use the interface of @{link Type} and
 * {@link TypeFactory}, the other classes in this package are not part of the
 * interface. To construct {@link IValue}'s, use the 'make' methods of
 * 
 * @{link Type}.
 *        <p>
 *        Technical detail: since void is a sub-type of all types and alias
 *        types may be sub-types of any types, a Java encoding of the hierarchy
 *        using single inheritance will not work. Therefore, all methods of all
 *        types are present on this abstract class Type. void and alias type
 *        implement all methods, while the other methods implement only the
 *        relevant methods. Calling a method that is not present on any of the
 *        specific types will lead to a @{link FactTypeError} exception.
 */
public abstract class Type implements Iterable<Type>, Comparable<Type> {
  protected static final TypeFactory TF = TypeFactory.getInstance();
  private static final TypeStore symbolStore = new TypeStore();
  private static final Type Symbol = TF.abstractDataType(symbolStore, "Symbol");
  private static final Type Symbol_Label = TF.constructor(symbolStore, Symbol, "label", TF.stringType(), "name", Symbol, "symbol");
  
  protected static boolean isLabel(IConstructor symbol) {
	  return symbol.getConstructorType() == Symbol_Label;
  }
  
  protected static String getLabel(IValue symbol) {
	  return ((IString) ((IConstructor) symbol).get("label")).getValue();
  }
  
  protected static IConstructor getLabeledSymbol(IValue symbol) {
	  return (IConstructor) ((IConstructor) symbol).get("symbol");
  }
  
  protected static Type declareTypeSymbol(String name, Object... args) {
	  return TF.constructor(symbolStore, symbolType(), name, args);
  }
  
  protected static Type declareTypeProduction(String name, Object... args) {
	  return TF.constructor(symbolStore, productionType(), name, args);
  }
  
  protected static Type symbolType() {
	  return TF.abstractDataType(symbolStore, "Symbol");
  }
  
  protected static Type productionType() {
	  return TF.abstractDataType(symbolStore, "Production");
  }
  
  
  
  // these constants are cached to avoid having to compute their hash-codes
  // for canonicalization all the time. The types are used to implement predicate
  // methods below such as isList and isMap, etc.
  private static final Type DATE_TIME_TYPE = TF.dateTimeType();
  private static final Type SOURCE_LOCATION_TYPE = TF.sourceLocationType();
  private static final Type STRING_TYPE = TF.stringType();
  private static final Type NODE_TYPE = TF.nodeType();
  private static final Type VOID_TYPE = TF.voidType();
  private static final Type VALUE_TYPE = TF.valueType();
  private static final Type NUMBER_TYPE = TF.numberType();
  private static final Type RATIONAL_TYPE = TF.rationalType();
  private static final Type REAL_TYPE = TF.realType();
  private static final Type INTEGER_TYPE = TF.integerType();
  private static final Type BOOL_TYPE = TF.boolType();
  private static final Type MAP_TYPE = TF.mapType(VALUE_TYPE, VALUE_TYPE);
  private static final Type LIST_TYPE = TF.listType(VALUE_TYPE);
  private static final Type SET_TYPE = TF.setType(VALUE_TYPE);
  
  /**
   * A store for reified type constructors which we use to map back from
   * reified type value to types.
   */
  private static final Map<Type, Method> fromSymbolMethods = new HashMap<>();
  
  protected abstract Type getReifiedConstructorType();
  
  static {
	  registerType(AbstractDataType.CONSTRUCTOR, AbstractDataType.class);
	  registerType(AliasType.CONSTRUCTOR, AliasType.class);
	  registerType(BoolType.CONSTRUCTOR, BoolType.class);
	  registerType(ConstructorType.CONSTRUCTOR, ConstructorType.class);
	  registerType(DateTimeType.CONSTRUCTOR, DateTimeType.class);
	  registerType(IntegerType.CONSTRUCTOR, ListType.class);
	  registerType(ListType.listConstructor, ListType.class);
	  registerType(MapType.CONSTRUCTOR, MapType.class);
	  registerType(NodeType.CONSTRUCTOR, NodeType.class);
	  registerType(NumberType.CONSTRUCTOR, NumberType.class);
	  registerType(ParameterType.CONSTRUCTOR, DateTimeType.class);
	  registerType(RationalType.CONSTRUCTOR, RationalType.class);
	  registerType(RealType.CONSTRUCTOR, RealType.class);
	  registerType(SetType.setConstructor, SetType.class);
	  registerType(SourceLocationType.CONSTRUCTOR, SourceLocationType.class);
	  registerType(StringType.CONSTRUCTOR, StringType.class);
	  registerType(TupleType.CONSTRUCTOR, TupleType.class);
	  registerType(ValueType.CONSTRUCTOR, ValueType.class);
	  registerType(VoidType.CONSTRUCTOR, VoidType.class);
  }
  
  protected static void registerType(Type cons, Class<? extends Type> type) {
	  synchronized (fromSymbolMethods) {
		  assert fromSymbolMethod(type) != null;
		  fromSymbolMethods.put(cons, fromSymbolMethod(type));
	  } 
  }
  
  /**
   * Builds a tuple type from a list of reified type symbols (see fromSymbol)
   */
  public static Type fromSymbols(IList symbols, TypeStore store, Function<IConstructor,Set<IConstructor>> grammar) {
	  boolean allLabels = true;
	  Type[] types = new Type[symbols.length()];
	  String[] labels = new String[symbols.length()];

	  for (int i = 0; i < symbols.length(); i++) {
		  IConstructor elem = (IConstructor) symbols.get(i);
		  if (elem.getConstructorType() == Symbol_Label) {
			  labels[i] = ((IString) elem.get("name")).getValue();
			  elem = (IConstructor) elem.get("symbol");
		  }
		  else {
			  allLabels = false;
		  }

		  types[i] = fromSymbol(elem, store, grammar);
	  }

	  if (allLabels) {
		  return TF.tupleType(types, labels);
	  }
	  else {
		  return TF.tupleType(types);
	  }
  }
  
  /**
   * Converts a value representing a type back to a type and as a side-effect declares all necessary
   * data-types and constructors in the provided typestore.
   * 
   * @param symbol is a constructor generated earlier by Type.asSymbol
   * @param store  is the typestore to store ADTs, constructors and kw fields in.
   * @param grammar is a lookup function to produce definitions for the types to store in the typestore
   * @return the type represented by the value
   */
  public static Type fromSymbol(IConstructor symbol, TypeStore store, Function<IConstructor,Set<IConstructor>> grammar) {
	  synchronized (fromSymbolMethods) {
		  Method fromSymbolMethod = fromSymbolMethods.get(symbol.getConstructorType());

		  if (fromSymbolMethod == null) {
			  throw new IllegalArgumentException("This is not a registered reified type symbol:" + symbol);
		  }

		  try {
			  return (Type) fromSymbolMethod.invoke(null /*static*/, symbol, store, grammar);
		  } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
			  throw new TypeReificationException("Could not create type from symbol:" + symbol, e);
		  }
	  }
  }

  private static Method fromSymbolMethod(Class<? extends Type> typeClass) {
	  try {
		  return typeClass.getMethod("fromSymbol", IConstructor.class, TypeStore.class, Function.class);
	  } catch (NoSuchMethodException e) {
		  throw new TypeReificationException("fromSymbol method is missing on " + typeClass, e);
	  }
  }
  
  protected IConstructor labelSymbol(IValueFactory vf, IConstructor symbol, String label) {
    return vf.constructor(Symbol_Label, vf.string(label), symbol);
  }
  
  /**
   * Retrieve the type of elements in a set or a relation.
   * 
   * @return type of elements
   */
  public Type getElementType() {
    throw new IllegalOperationException("getElementType", this);
  }

  /**
   * Retrieve the key type of a map
   * 
   * @return key type
   */
  public Type getKeyType() {
    throw new IllegalOperationException("getKeyType", this);
  }

  /**
   * Retrieve the value type of a map
   * 
   * @return value type
   */
  public Type getValueType() {
    throw new IllegalOperationException("getValueType", this);
  }

  /**
   * Retrieve the name of a named type, a tree node type or a parameter type.
   * 
   * @return name of the type
   */
  public String getName() {
    throw new IllegalOperationException("getName", this);
  }

  /**
   * Retrieve the type of a field of a tuple type, a relation type or a tree
   * node type.
   * 
   * @param i
   *          index of the field to retrieve
   * @return type of the field at index i
   */
  public Type getFieldType(int i) {
    throw new IllegalOperationException("getFieldType", this);
  }

  /**
   * Retrieve the type of a field of a tuple type, a relation type or a tree
   * node type.
   * <p>
   * 
   * @param fieldName
   *          label of the field to retrieve
   * @return type of the field at index i
   * @throws FactTypeUseException
   *           when the type has no field labels (tuples and relations
   *           optionally have field labels).          
   */
  public Type getFieldType(String fieldName) throws FactTypeUseException {
    throw new IllegalOperationException("getFieldType", this);
  }

  /**
   * Retrieve the field types of a tree node type or a relation, represented as
   * a tuple type.
   * 
   * @return a tuple type representing the field types
   */
  public Type getFieldTypes() {
    throw new IllegalOperationException("getFieldTypes", this);
  }

  /**
   * Retrieve the field name at a certain index for a tuple type, a relation
   * type or a tree node type.
   * 
   * @param i
   *          index of the field name to retrieve
   * @return the field name at index i
   * @throws FactTypeUseException
   *           when this type does not have field labels. Tuples and relations
   *           optionally have field labels.
   */
  public String getFieldName(int i) {
    throw new IllegalOperationException("getFieldName", this);
  }

  /**
   * Retrieve all the field names of tuple type, a relation type or a tree node
   * type.
   * 
   * @return the field name at index i
   * @throws FactTypeUseException
   *           when this type does not have field labels. Tuples and relations
   *           optionally have field labels.
   */
  public String[] getFieldNames() {
    throw new IllegalOperationException("getFieldNames", this);
  }

  /**
   * Retrieve a field index for a certain label for a tuple type, a relation
   * type or a tree node type.
   * 
   * @param fieldName
   *          name of the field to retrieve
   * @return the index of fieldName
   */
  public int getFieldIndex(String fieldName) {
    throw new IllegalOperationException("getFieldIndex", this);
  }

  /**
   * @param fieldName
   *          name of the field to check for
   * @return true iff this type has a field named fieldName
   */
  public boolean hasField(String fieldName) {
    throw new IllegalOperationException("hasField", this);
  }

  /**
   * @param fieldName
   *          name of the field to check for
   * @return true iff this type has a field named fieldName
   */
  public boolean hasField(String fieldName, TypeStore store) {
    return hasField(fieldName);
  }
  
  /**
   * @param fieldName
   *          name of the keyword field to check for
   * @return true iff this type has a keyword field named fieldName
   */
  public boolean hasKeywordField(String fieldName, TypeStore store) {
	  throw new IllegalOperationException("hasKeywordField", this);
  }

  /**
   * Retrieve the width, a.k.a. arity, of a tuple, a relation or a tree node
   * type.
   * 
   * @return the arity
   */
  public int getArity() {
    throw new IllegalOperationException("getArity", this);
  }
  
  /**
   * Represent this type as a value of the abstract data-type "Symbol". As a side-effect
   * it will also add Production values to the grammar map, including all necessary productions
   * to build values of the receiver type, transitively.
   * 
   * @param  vf valuefactory to use 
   * @param store store to lookup additional necessary definitions in to store in the grammar
   * @param grammar map to store production values in as a side-effect
   * @param done a working set to store data-types which have been explored already to avoid infinite recursion
   * @return a value to uniquely represent this type.
   */
  public abstract IConstructor asSymbol(IValueFactory vf, TypeStore store, ISetWriter grammar, Set<IConstructor> done);

  /**
   * Map the given typestore to a set of production values, with only definitions
   * reachable from the receiver type
   * 
   * @param  vf valuefactory to use 
   * @param  store typestore which contains source definitions
   * @param done a working set to store data-types which have been explored already to avoid infinite recursion
   */
  protected abstract void asProductions(IValueFactory vf, TypeStore store, ISetWriter grammar, Set<IConstructor> done);

  
  /**
   * Compose two binary tuples or binary relation types.
   * 
   * @param other
   * @return a new type that represent the composition
   * @throws IllegalOperationException
   *           if the receiver or the other is not binary or if the last type of
   *           the receiver is not comparable to the first type of the other.
   */
  public Type compose(Type other) {
    throw new IllegalOperationException("compose", this, other);
  }

  /**
   * For relation types rel[t_1,t_2] this will compute rel[t_3,t_3] where t_3 =
   * t_1.lub(t_2).
   * 
   * @return rel[t_3,t_3]
   * @throws IllegalOperationException
   *           when this is not a binary relation or t_1 is not comparable to
   *           t_2 (i.e. the relation is not reflexive)
   */
  public Type closure() {
    throw new IllegalOperationException("closure", this);
  }

  /**
   * Computes the least upper bound of all elements of this type and returns a
   * set of this type. Works on all types that have elements/fields or children
   * such as tuples, relations, sets and constructors.
   * 
   * @return a set[lub].
   */
  public Type carrier() {
    throw new IllegalOperationException("carrier", this);
  }

  /**
   * Iterate over fields of the type
   */
  public Iterator<Type> iterator() {
    throw new IllegalOperationException("iterator", this);
  }

  /**
   * Select fields from tuples and relation
   * 
   * @param fields
   * @return a new tuple or relation type with the selected fields
   */
  public Type select(int... fields) {
    throw new IllegalOperationException("select", this);

  }

  /**
   * Select fields from tuples and relation
   * 
   * @param fields
   * @return a new tuple or relation type with the selected fields
   */
  public Type select(String... names) {
    throw new IllegalOperationException("select", this);
  }

  /**
   * For a constructor, return the algebraic data-type it constructs
   * 
   * @return a type
   */
  public Type getAbstractDataType() {
    throw new IllegalOperationException("getAbstractDataType", this);
  }

  /**
   * For an alias type, return which type it aliases.
   * 
   * @return a type
   */
  public Type getAliased() {
    throw new IllegalOperationException("getAliased", this);
  }

  /**
   * For a parameter type, return its bound
   * 
   * @return a type
   */
  public Type getBound() {
    throw new IllegalOperationException("getBound", this);
  }

  /**
   * For a tuple type or a relation type, determine whether the fields are
   * labelled or not.
   * 
   * @return if the fields of a type or relation have been labelled
   */
  public boolean hasFieldNames() {
    return false;
  }

  /**
   * For a AbstractDataType or a ConstructorType, return whether a certain
   * annotation label was declared.
   * 
   * @param label
   * @param store
   *          to find the declaration in
   * @return true if this type has an annotation named label declared for it.
   */
  public boolean declaresAnnotation(TypeStore store, String label) {
    return false;
  }

  public Type getAnnotationType(TypeStore store, String label) throws FactTypeUseException {
    throw new IllegalOperationException("getAnnotationType", this);
  }

  public String getKeyLabel() {
    throw new IllegalOperationException("getKeyLabel", this);
  }

  public String getValueLabel() {
    throw new IllegalOperationException("getValueLabel", this);
  }

  /**
   * @return the least upper bound type of the receiver and the argument type
   */
  public abstract Type lub(Type type);

  public abstract Type glb(Type type);
  
  /**
   * The sub-type relation. Value is the biggest type and void is the smallest.
   * Value is the top and void is the bottom of the type hierarchy.
   * 
   * @param other
   * @return true if the receiver is a subtype of the other type
   */
  public final boolean isSubtypeOf(Type other) {
    return other == this || other.isSupertypeOf(this);
  }
  
  public final boolean isStrictSubtypeOf(Type other) {
    return (!other.equivalent(this)) && other.isSupertypeOf(this);
  }

  protected abstract boolean isSupertypeOf(Type type);

  /**
   * Return whether an ADT or an alias Type has any type parameters
   * 
   * @return true if the type is parameterized
   */
  public boolean isParameterized() {
    return false;
  }
  
  /**
   * @return true iff the type contains any uninstantiated type parameters 
   */
  public boolean isOpen() {
    return false;
  }
  
  /**
   * @return true iff the type is an alias
   */
  public boolean isAliased() {
    return false;
  }
  
  public final boolean isSet() {
    return isSubtypeOf(SET_TYPE);
  }
  
  public final boolean isList() {
    return isSubtypeOf(LIST_TYPE);
  }
  
  public final boolean isMap() {
    return isSubtypeOf(MAP_TYPE);
  }
  
  public final boolean isBool() {
    return isSubtypeOf(BOOL_TYPE);
  }
  
  public final boolean isRelation() {
    return isSet() && getElementType().isFixedWidth();
  }
  
  public final boolean isListRelation() {
    return isList() && getElementType().isFixedWidth();
  }
  
  public final boolean isInteger() {
    return isSubtypeOf(INTEGER_TYPE);
  }
  
  public final boolean isReal() {
    return isSubtypeOf(REAL_TYPE);
  }
  
  public final boolean isRational() {
    return isSubtypeOf(RATIONAL_TYPE);
  }
  
  public final boolean isNumber() {
    return isSubtypeOf(NUMBER_TYPE);
  }
  
  public final boolean isTop() {
    return equivalent(VALUE_TYPE);
  }
  
  public final boolean isBottom() {
    return equivalent(VOID_TYPE);
  }
  
  public final boolean isNode() {
	  return isSubtypeOf(NODE_TYPE);
  }
  
  public final boolean isAbstractData() {
    return isStrictSubtypeOf(NODE_TYPE);
  }
  
  public final boolean isConstructor() {
	  return isAbstractData() && !this.equivalent(this.getAbstractDataType());
  }
  
  public final boolean isString() {
	  return isSubtypeOf(STRING_TYPE);
  }
  
  public final boolean isSourceLocation() {
    return isSubtypeOf(SOURCE_LOCATION_TYPE);
  }
  
  public final boolean isDateTime() {
	  return isSubtypeOf(DATE_TIME_TYPE);
  }
  
  public final boolean isTuple() {
	  return isFixedWidth();
  }
  
  public boolean isExternalType() {
    return false;
  }
  
  /**
   * @return true iff type is a tuple
   */
  public boolean isFixedWidth() {
    return false;
  }

  /**
   * Compute whether this type is a subtype of the other or vice versa
   * 
   * @param other
   *          type to compare to
   * @return true if the types are comparable.
   */
  public final boolean comparable(Type other) {
    return (other == this) || isSubtypeOf(other) || other.isSubtypeOf(this);
  }

  /**
   * Computer whether this type is equivalent to another.
   * 
   * @param other
   *          type to compare to
   * @return true if the two types are sub-types of each-other;
   */
  public final boolean equivalent(Type other) {
    return (other == this) || (isSubtypeOf(other) && other.isSubtypeOf(this));
  }

  /**
   * If this type has parameters and there are parameter types embedded in it,
   * instantiate will replace the parameter types using the given bindings.
   * 
   * @param bindings
   *          a map from parameter type names to actual types.
   * @return a type with all parameter types substituted.
   */
  public Type instantiate(Map<Type, Type> bindings) {
    return this;
  }

  /**
   * Construct a map of parameter type names to actual type names. The receiver
   * is a pattern that may contain parameter types.
   * 
   * @param matched
   *          a type to matched to the receiver.
   * @throws FactTypeUseException
   *           when a pattern can not be matches because the matched types do
   *           not fit the bounds of the parameter types, or when a pattern
   *           simply can not be matched because of incompatibility.
   */
  public boolean match(Type matched, Map<Type, Type> bindings) throws FactTypeUseException {
    return matched.isSubtypeOf(this);
  }

  public abstract <T,E extends Throwable> T accept(ITypeVisitor<T,E> visitor) throws E;

  /**
   * For alias types and adt types return which type parameters there are.
   * 
   * @return void if there are no type parameters, or a tuple of type parameters
   *         otherwise.
   */
  public Type getTypeParameters() {
    throw new IllegalOperationException("getTypeParameters", this);
  }

  /**
   * Compare against another type.
   * 
   * A type is 'less' than another if it is a subtype, 'greater' if the other is
   * a subtype, or 'equal' if both are subtypes of each other.
   * 
   * Note: this class has a natural ordering that is inconsistent with equals.
   * equals() on types is exact equality, which may be different from
   * compareTo(o) == 0
   * 
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Type o) {
    if (isSubtypeOf(o)) {
      return -1;
    } else if (o.isSubtypeOf(this)) {
      return 1;
    }
    return 0;
  }

  protected boolean isSubtypeOfParameter(Type type) {
    return isSubtypeOf(type.getBound());
  }
  
  protected final boolean isSubtypeOfAlias(Type type) {
    return isSubtypeOf(type.getAliased());
  }
  
  abstract protected boolean isSubtypeOfReal(Type type);
  abstract protected boolean isSubtypeOfInteger(Type type);
  abstract protected boolean isSubtypeOfRational(Type type);
  abstract protected boolean isSubtypeOfList(Type type);
  abstract protected boolean isSubtypeOfMap(Type type);
  abstract protected boolean isSubtypeOfNumber(Type type);
  abstract protected boolean isSubtypeOfRelation(Type type);
  abstract protected boolean isSubtypeOfListRelation(Type type);
  abstract protected boolean isSubtypeOfSet(Type type);
  abstract protected boolean isSubtypeOfSourceLocation(Type type);
  abstract protected boolean isSubtypeOfString(Type type);
  abstract protected boolean isSubtypeOfNode(Type type);
  abstract protected boolean isSubtypeOfConstructor(Type type);
  abstract protected boolean isSubtypeOfAbstractData(Type type);
  abstract protected boolean isSubtypeOfTuple(Type type);
  abstract protected boolean isSubtypeOfValue(Type type);
  abstract protected boolean isSubtypeOfVoid(Type type);
  abstract protected boolean isSubtypeOfBool(Type type);
  abstract protected boolean isSubtypeOfExternal(Type type);
  abstract protected boolean isSubtypeOfDateTime(Type type);
  
  protected Type lubWithAlias(Type type) {
    return lub(type.getAliased());
  }
  
  protected Type lubWithParameter(Type type) {
    return lub(type.getBound());
  }
  
  abstract protected Type lubWithReal(Type type) ;
  abstract protected Type lubWithInteger(Type type) ;
  abstract protected Type lubWithRational(Type type) ;
  abstract protected Type lubWithList(Type type) ;
  abstract protected Type lubWithMap(Type type) ;
  abstract protected Type lubWithNumber(Type type) ;
  abstract protected Type lubWithSet(Type type) ;
  abstract protected Type lubWithSourceLocation(Type type) ;
  abstract protected Type lubWithString(Type type) ;
  abstract protected Type lubWithNode(Type type) ;
  abstract protected Type lubWithConstructor(Type type) ;
  abstract protected Type lubWithAbstractData(Type type) ;
  abstract protected Type lubWithTuple(Type type) ;
  abstract protected Type lubWithValue(Type type) ;
  abstract protected Type lubWithVoid(Type type) ;
  abstract protected Type lubWithBool(Type type) ;
  abstract protected Type lubWithDateTime(Type type) ;
  
  protected Type glbWithAlias(Type type) {
    return glb(type.getAliased());
  }
  
  protected Type glbWithParameter(Type type) {
    return glb(type.getBound());
  }
  
  abstract protected Type glbWithReal(Type type) ;
  abstract protected Type glbWithInteger(Type type) ;
  abstract protected Type glbWithRational(Type type) ;
  abstract protected Type glbWithList(Type type) ;
  abstract protected Type glbWithMap(Type type) ;
  abstract protected Type glbWithNumber(Type type) ;
  abstract protected Type glbWithSet(Type type) ;
  abstract protected Type glbWithSourceLocation(Type type) ;
  abstract protected Type glbWithString(Type type) ;
  abstract protected Type glbWithNode(Type type) ;
  abstract protected Type glbWithConstructor(Type type) ;
  abstract protected Type glbWithAbstractData(Type type) ;
  abstract protected Type glbWithTuple(Type type) ;
  abstract protected Type glbWithValue(Type type) ;
  abstract protected Type glbWithVoid(Type type) ;
  abstract protected Type glbWithBool(Type type) ;
  abstract protected Type glbWithDateTime(Type type) ;
  
  /**
   * This makes sure that lubbing can be done by the external type whether
   * or not it is the initial receiver or the second parameter to lub.
   */
  protected Type lubWithExternal(Type type) {
    // the external type should be the receiver
    return lub(type);
  }
  
  protected Type glbWithExternal(Type type) {
    // the external type should be the receiver
    return glb(type);
  }
}