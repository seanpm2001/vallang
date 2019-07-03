package io.usethesource.vallang.impl.persistent;

import java.util.Iterator;

import io.usethesource.vallang.IRelation;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ITuple;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;
import io.usethesource.vallang.IWriter;
import io.usethesource.vallang.exceptions.IllegalOperationException;
import io.usethesource.vallang.impl.util.collections.ShareableValuesHashSet;
import io.usethesource.vallang.impl.util.collections.ShareableValuesList;
import io.usethesource.vallang.util.RotatingQueue;
import io.usethesource.vallang.util.ShareableHashMap;
import io.usethesource.vallang.util.ValueIndexedHashMap;

public class PersistentSetRelation implements IRelation<ISet> {
    private final ISet set;

    public PersistentSetRelation(ISet set) {
        this.set = set;
    }
    
    @Override
    public ISet asContainer() {
        return set;
    }

    @Override
    public ISet compose(IRelation<ISet> set2) {
        if (this.getElementType().isBottom()) {
            return set;
        }
        
        if (set2.getElementType().isBottom()) {
            return set2.asContainer();
        }

        if (this.getElementType().getArity() != 2
                || set2.getElementType().getArity() != 2) {
            throw new IllegalOperationException(
                    "Incompatible types for composition.",
                    this.getElementType(), set2.getElementType());
        }
        
        if (!this.getElementType().getFieldType(1)
                .comparable(set2.getElementType().getFieldType(0))) {
            return EmptySet.of();
        }
        
        // TODO: recomputing this index is weird, since it can be expected a
        // relation is already indexed.
        
        // Index
        ShareableHashMap<IValue, ShareableValuesList> rightSides = new ShareableHashMap<>();
        
        for (IValue val : set2) {
            ITuple tuple = (ITuple) val;
            IValue key = tuple.get(0);
            ShareableValuesList values = rightSides.get(key);
            if (values == null) {
                values = new ShareableValuesList();
                rightSides.put(key, values);
            }
            
            values.append(tuple.get(1));
        }

        // build result using index
        
        IWriter<ISet> resultWriter = writer();
        for (IValue thisVal : this) {
            ITuple thisTuple = (ITuple) thisVal;
            IValue key = thisTuple.get(1);
            ShareableValuesList values = rightSides.get(key);
            if (values != null){
                Iterator<IValue> valuesIterator = values.iterator();
                do {
                    IValue value = valuesIterator.next();
                    resultWriter.insertTuple(thisTuple.get(0), value);
                } while(valuesIterator.hasNext());
            }
        }
        
        return resultWriter.done();
    }
    
    @Override
    public ISet closure() {
        IWriter<ISet> resultWriter = writer();
        resultWriter.insertAll(this);
        resultWriter.insertAll(computeClosureDelta());
        return resultWriter.done();
    }

    
    @Override
    public ISet closureStar() {
        // calculate
        ShareableValuesHashSet closureDelta = computeClosureDelta();

        IWriter<ISet> resultWriter = writer();
        resultWriter.insertAll(this);
        resultWriter.insertAll(closureDelta);
        
        for (IValue element : carrier()) {
            resultWriter.insertTuple(element, element);
        }

        return resultWriter.done();
    }
    
    private ShareableValuesHashSet computeClosureDelta() {
        IValueFactory vf = ValueFactory.getInstance();
        RotatingQueue<IValue> iLeftKeys = new RotatingQueue<>();
        RotatingQueue<RotatingQueue<IValue>> iLefts = new RotatingQueue<>();
        
        ValueIndexedHashMap<RotatingQueue<IValue>> interestingLeftSides = new ValueIndexedHashMap<>();
        ValueIndexedHashMap<ShareableValuesHashSet> potentialRightSides = new ValueIndexedHashMap<>();
        
        // Index
        for (IValue val : this) {
            ITuple tuple = (ITuple) val;
            IValue key = tuple.get(0);
            IValue value = tuple.get(1);
            RotatingQueue<IValue> leftValues = interestingLeftSides.get(key);
            ShareableValuesHashSet rightValues;
            
            if (leftValues != null) {
                rightValues = potentialRightSides.get(key);
            } else {
                leftValues = new RotatingQueue<>();
                iLeftKeys.put(key);
                iLefts.put(leftValues);
                interestingLeftSides.put(key, leftValues);
                
                rightValues = new ShareableValuesHashSet();
                potentialRightSides.put(key, rightValues);
            }
            
            leftValues.put(value);
            rightValues.add(value);
        }
        
        int size = potentialRightSides.size();
        int nextSize = 0;
        
        // Compute
        final ShareableValuesHashSet newTuples = new ShareableValuesHashSet();
        do{
            ValueIndexedHashMap<ShareableValuesHashSet> rightSides = potentialRightSides;
            potentialRightSides = new ValueIndexedHashMap<>();
            
            for(; size > 0; size--){
                IValue leftKey = iLeftKeys.get();
                RotatingQueue<IValue> leftValues = iLefts.get();
                
                RotatingQueue<IValue> interestingLeftValues = null;
                
                IValue rightKey;
                while((rightKey = leftValues.get()) != null){
                    ShareableValuesHashSet rightValues = rightSides.get(rightKey);
                    if(rightValues != null){
                        Iterator<IValue> rightValuesIterator = rightValues.iterator();
                        while(rightValuesIterator.hasNext()){
                            IValue rightValue = rightValuesIterator.next();
                            if(newTuples.add(vf.tuple(leftKey, rightValue))){
                                if(interestingLeftValues == null){
                                    nextSize++;
                                    
                                    iLeftKeys.put(leftKey);
                                    interestingLeftValues = new RotatingQueue<>();
                                    iLefts.put(interestingLeftValues);
                                }
                                interestingLeftValues.put(rightValue);
                                
                                ShareableValuesHashSet potentialRightValues = potentialRightSides.get(rightKey);
                                if(potentialRightValues == null){
                                    potentialRightValues = new ShareableValuesHashSet();
                                    potentialRightSides.put(rightKey, potentialRightValues);
                                }
                                potentialRightValues.add(rightValue);
                            }
                        }
                    }
                }
            }
            size = nextSize;
            nextSize = 0;
        } while(size > 0);
        
        return newTuples;
    }
    
}