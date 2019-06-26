package io.usethesource.vallang.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.StreamSupport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import io.usethesource.vallang.ExpectedType;
import io.usethesource.vallang.IInteger;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.ValueProvider;

public class SetTests {

    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void idempotence( @ExpectedType("set[int]") ISet set, IInteger i) {
        assertEquals(set.insert(i), set.insert(i).insert(i));
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void insertion( @ExpectedType("set[real]") ISet set, IInteger i) {
        assertEquals(set.insert(i).size(), set.size() + 1);
    }
    
    @ParameterizedTest @ArgumentsSource(ValueProvider.class)
    public void removal( @ExpectedType("set[int]") ISet set, IInteger i) {
        if (set.isEmpty()) {
            return;
        }
        
        IValue elem = StreamSupport.stream(set.spliterator(), false).skip(Math.abs(i.intValue() % set.size())).findFirst().get();
        ISet smaller = set.delete(elem);
        assertEquals(smaller.size() + 1, set.size());
        assertTrue(!smaller.contains(elem));
    }
}
