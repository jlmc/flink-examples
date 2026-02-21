package io.github.jlmc.flink.transformations.richfunctions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamTest {

    private static final Set<String> MARVEL =
            Set.of(
                    "ironman",
                    "iron man",
                    "captainamerica",
                    "captain america",
                    "thor",
                    "hulk",
                    "blackwidow",
                    "spiderman",
                    "spider man",
                    "doctorstrange",
                    "doctor strange",
                    "blackpanther",
                    "scarletwitch",
                    "wolverine"
            );
    public static final List<String> SOURCE = List.of(
            // Marvel Heroes
            "Iron Man", "Captain America", "Thor", "Hulk", "BlackWidow", "Spider Man", "DoctorStrange", "BlackPanther", "ScarletWitch", "Wolverine",
            // DC Heroes
            "Super man", "Bat man", "WonderWoman", "Flash", "Aqua man", "Cyborg", "GreenLantern", "Shazam", "Supergirl", "Nightwing"
    );

    static String camelCaseToWords(String text) {
        if (text == null || text.isBlank()) return "";

        return text
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    public static Stream<Arguments> heroCollections() {

        Map<Boolean, Map<String, Long>> expected = Map.of(
                false, new LinkedHashMap<>(
                ) {
                    {
                        put("man", 3L);
                        put("aqua", 1L);
                        put("bat", 1L);
                        put("cyborg", 1L);
                        put("flash", 1L);
                        put("green", 1L);
                        put("lantern", 1L);
                        put("nightwing", 1L);
                        put("shazam", 1L);
                        put("super", 1L);
                        put("supergirl", 1L);
                        put("woman", 1L);
                        put("wonder", 1L);
                    }
                },
                true, new LinkedHashMap<>() {
                    {
                        put("black", 2L);
                        put("man", 2L);
                        put("america", 1L);
                        put("captain", 1L);
                        put("doctor", 1L);
                        put("hulk", 1L);
                        put("iron", 1L);
                        put("panther", 1L);
                        put("scarlet", 1L);
                        put("spider", 1L);
                        put("strange", 1L);
                        put("thor", 1L);
                        put("widow", 1L);
                        put("witch", 1L);
                        put("wolverine", 1L);
                    }
                }
        );


        return Stream.of(
                Arguments.of(SOURCE, expected)
                //Arguments.of(List.of("Iron Man", "Captain America", "Thor", "Hulk", "BlackWidow", "SpiderMan", "DoctorStrange", "BlackPanther", "ScarletWitch", "Wolverine")),
                //Arguments.of(List.of("Superman", "Bat man", "WonderWoman", "Flash", "Aqua man", "Cyborg", "GreenLantern", "Shazam", "Supergirl", "Nightwing"))
        );
    }

    boolean isMarvelHero(String hero) {
        return hero != null && MARVEL.contains(hero.toLowerCase());
    }
    
    @Test
    void when_partitionBy() {
        List<String> source = SOURCE;

        var collected =
                source.stream().collect(
                        Collectors.partitioningBy(
                                this::isMarvelHero,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(
                                                hero -> camelCaseToWords(hero).toLowerCase(),
                                                Collectors.toSet()
                                        ),
                                        set -> set.stream().map(String::toLowerCase).sorted().collect(Collectors.joining(", "))
                                ))
                );

        System.out.println(collected);


        Map<Boolean, Map<String, Long>> collected2 =
                source.stream().collect(
                        Collectors.partitioningBy(
                                this::isMarvelHero,
                                Collectors.collectingAndThen(
                                        Collectors.mapping(
                                                hero -> camelCaseToWords(hero).toLowerCase(),
                                                Collectors.toList()
                                        ),
                                        list -> list.stream().collect(Collectors.groupingBy(it -> it, Collectors.counting()))
                                ))
                );

        System.out.println(collected2);
    }

    @ParameterizedTest
    @MethodSource("heroCollections")
    void countHeroWordOccurrencesByUniverse(Collection<String> source, Map<Boolean, Map<String, Long>> expected ) {

        Map<Boolean, Map<String, Long>> collected =
                source.stream().collect(
                        Collectors.partitioningBy(
                                this::isMarvelHero,

                                Collectors.collectingAndThen(

                                        Collectors.flatMapping(
                                                hero -> Arrays.stream(
                                                        camelCaseToWords(hero)
                                                                .toLowerCase(Locale.ROOT)
                                                                .split("[^a-z]+")
                                                ).filter(s -> !s.isBlank()),

                                                Collectors.groupingBy(
                                                        word -> word,
                                                        Collectors.counting()
                                                )
                                        ),

                                        map -> map.entrySet()
                                                .stream()
                                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                                        .thenComparing(Map.Entry.comparingByKey()))
                                                .collect(Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        Map.Entry::getValue,
                                                        (a, b) -> a,
                                                        LinkedHashMap::new
                                                ))
                                )
                        )
                );

        Assertions.assertEquals(expected, collected);
        // Assert order explicitly
        expected.forEach((universe, expectedMap) -> {
            Map<String, Long> actualMap = collected.get(universe);
            Assertions.assertEquals(expectedMap.keySet(), actualMap.keySet(), "Keys order for universe " + universe + " must match");
            Assertions.assertIterableEquals(expectedMap.entrySet(), actualMap.entrySet(), "Entries order for universe " + universe + " must match");
        });
    }
}
