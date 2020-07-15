/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.utils;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.core.ml.utils.NameResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class NameResolverTests extends ESTestCase {

    public void testNoMatchingNames() {
        assertThat(newUnaliasedResolver().expand("foo").isEmpty(), is(true));
    }

    public void testNoMatchingNames_GivenPattern() {
        assertThat(newUnaliasedResolver().expand("foo*").isEmpty(), is(true));
    }

    public void testNoMatchingNames_GivenMatchingNameAndNonMatchingPattern() {
        NameResolver nameResolver = newUnaliasedResolver("foo");
        assertThat(nameResolver.expand("foo,bar*"), equalTo(newSortedSet("foo")));
    }

    public void testUnaliased() {
        NameResolver nameResolver = newUnaliasedResolver("foo-1", "foo-2", "bar-1", "bar-2");

        assertThat(nameResolver.expand("foo-1"), equalTo(newSortedSet("foo-1")));
        assertThat(nameResolver.expand("foo-2"), equalTo(newSortedSet("foo-2")));
        assertThat(nameResolver.expand("bar-1"), equalTo(newSortedSet("bar-1")));
        assertThat(nameResolver.expand("bar-2"), equalTo(newSortedSet("bar-2")));
        assertThat(nameResolver.expand("foo-1,foo-2"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-*"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("bar-*"), equalTo(newSortedSet("bar-1", "bar-2")));
        assertThat(nameResolver.expand("*oo-*"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("*-1"), equalTo(newSortedSet("foo-1", "bar-1")));
        assertThat(nameResolver.expand("*-2"), equalTo(newSortedSet("foo-2", "bar-2")));
        assertThat(nameResolver.expand("*"), equalTo(newSortedSet("foo-1", "foo-2", "bar-1", "bar-2")));
        assertThat(nameResolver.expand("_all"), equalTo(newSortedSet("foo-1", "foo-2", "bar-1", "bar-2")));
        assertThat(nameResolver.expand("foo-1,foo-2"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-1,bar-1"), equalTo(newSortedSet("bar-1", "foo-1")));
        assertThat(nameResolver.expand("foo-*,bar-1"), equalTo(newSortedSet("bar-1", "foo-1", "foo-2")));
    }

    public void testAliased() {
        Map<String, List<String>> namesAndAliasesMap = new HashMap<>();
        namesAndAliasesMap.put("foo-1", Collections.singletonList("foo-1"));
        namesAndAliasesMap.put("foo-2", Collections.singletonList("foo-2"));
        namesAndAliasesMap.put("bar-1", Collections.singletonList("bar-1"));
        namesAndAliasesMap.put("bar-2", Collections.singletonList("bar-2"));
        namesAndAliasesMap.put("foo-group", Arrays.asList("foo-1", "foo-2"));
        namesAndAliasesMap.put("bar-group", Arrays.asList("bar-1", "bar-2"));
        NameResolver nameResolver = new TestAliasNameResolver(namesAndAliasesMap);

        // First try same set of assertions as unaliases
        assertThat(nameResolver.expand("foo-1"), equalTo(newSortedSet("foo-1")));
        assertThat(nameResolver.expand("foo-2"), equalTo(newSortedSet("foo-2")));
        assertThat(nameResolver.expand("bar-1"), equalTo(newSortedSet("bar-1")));
        assertThat(nameResolver.expand("bar-2"), equalTo(newSortedSet("bar-2")));
        assertThat(nameResolver.expand("foo-1,foo-2"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-*"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("bar-*"), equalTo(newSortedSet("bar-1", "bar-2")));
        assertThat(nameResolver.expand("*oo-*"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("*-1"), equalTo(newSortedSet("foo-1", "bar-1")));
        assertThat(nameResolver.expand("*-2"), equalTo(newSortedSet("foo-2", "bar-2")));
        assertThat(nameResolver.expand("*"), equalTo(newSortedSet("foo-1", "foo-2", "bar-1", "bar-2")));
        assertThat(nameResolver.expand("_all"), equalTo(newSortedSet("foo-1", "foo-2", "bar-1", "bar-2")));
        assertThat(nameResolver.expand("foo-1,foo-2"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-1,bar-1"), equalTo(newSortedSet("bar-1", "foo-1")));
        assertThat(nameResolver.expand("foo-*,bar-1"), equalTo(newSortedSet("bar-1", "foo-1", "foo-2")));

        // No let's test the aliases
        assertThat(nameResolver.expand("foo-group"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("bar-group"), equalTo(newSortedSet("bar-1", "bar-2")));
        assertThat(nameResolver.expand("foo-group,bar-group"), equalTo(newSortedSet("bar-1", "bar-2", "foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-group,foo-1"), equalTo(newSortedSet("foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-group,bar-1"), equalTo(newSortedSet("bar-1", "foo-1", "foo-2")));
        assertThat(nameResolver.expand("foo-group,bar-*"), equalTo(newSortedSet("bar-1", "bar-2", "foo-1", "foo-2")));
    }

    private static NameResolver newUnaliasedResolver(String... names) {
        return NameResolver.newUnaliased(new HashSet<>(Arrays.asList(names)));
    }

    private static SortedSet<String> newSortedSet(String... names) {
        SortedSet<String> result = new TreeSet<>();
        for (String name : names) {
            result.add(name);
        }
        return result;
    }
    
    private static class TestAliasNameResolver extends NameResolver {

        private final Map<String, List<String>> lookup;

        TestAliasNameResolver(Map<String, List<String>> lookup) {
            this.lookup = lookup;
        }

        @Override
        protected Set<String> keys() {
            return lookup.keySet();
        }

        @Override
        protected Set<String> nameSet() {
            return lookup.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        }

        @Override
        protected List<String> lookup(String key) {
            return lookup.containsKey(key) ? lookup.get(key) : Collections.emptyList();
        }
    }
}