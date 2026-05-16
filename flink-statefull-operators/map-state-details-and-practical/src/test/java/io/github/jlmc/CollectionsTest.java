package io.github.jlmc;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class CollectionsTest {

    @Test
    void nCopiesExample() {
        var collections = Collections.nCopies(5, "hello");

        System.out.printf("collections: %s%n", collections);

        List<AddressState> addressStates = Collections.nCopies(5, new AddressState("key", "value"));

        // check if all instances are the same
        boolean allSame = addressStates.stream().allMatch(s -> s == addressStates.get(0));
        System.out.printf("All instances are the same: %b%n", allSame);
    }

    record AddressState(String key, String value) {}
}
