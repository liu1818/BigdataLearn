/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.utils;

import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.regex.Regex;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Expands an expression into the set of matching names.
 * It optionally supports aliases to the name set.
 */
public abstract class NameResolver {

    /**
     * Expands an expression into the set of matching names.
     * For example, given a set of names ["foo-1", "foo-2", "bar-1", bar-2"],
     * expressions resolve follows:
     * <ul>
     *     <li>"foo-1" : ["foo-1"]</li>
     *     <li>"bar-1" : ["bar-1"]</li>
     *     <li>"foo-1,foo-2" : ["foo-1", "foo-2"]</li>
     *     <li>"foo-*" : ["foo-1", "foo-2"]</li>
     *     <li>"*-1" : ["bar-1", "foo-1"]</li>
     *     <li>"*" : ["bar-1", "bar-2", "foo-1", "foo-2"]</li>
     *     <li>"_all" : ["bar-1", "bar-2", "foo-1", "foo-2"]</li>
     * </ul>
     *
     * @param expression the expression to resolve
     * @return the sorted set of matching names
     */
    public SortedSet<String> expand(String expression) {
        SortedSet<String> result = new TreeSet<>();
        if (MetaData.ALL.equals(expression) || Regex.isMatchAllPattern(expression)) {
            result.addAll(nameSet());
        } else {
            String[] tokens = Strings.tokenizeToStringArray(expression, ",");
            for (String token : tokens) {
                if (Regex.isSimpleMatchPattern(token)) {
                    List<String> expanded = keys().stream()
                            .filter(key -> Regex.simpleMatch(token, key))
                            .map(this::lookup)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    result.addAll(expanded);
                } else {
                    List<String> matchingNames = lookup(token);
                    result.addAll(matchingNames);
                }
            }
        }
        return result;
    }

    /**
     * @return the set of registered keys
     */
    protected abstract Set<String> keys();

    /**
     * @return the set of all names
     */
    protected abstract Set<String> nameSet();

    /**
     * Looks up a key and returns the matching names.
     * @param key the key to look up
     * @return a list of the matching names or {@code null} when no matching names exist
     */
    protected abstract List<String> lookup(String key);

    /**
     * Creates a {@code NameResolver} that has no aliases
     * @param nameSet the set of all names
     * @return the unaliased {@code NameResolver}
     */
    public static NameResolver newUnaliased(Set<String> nameSet) {
        return new NameResolver() {
            @Override
            protected Set<String> keys() {
                return nameSet;
            }

            @Override
            protected Set<String> nameSet() {
                return nameSet;
            }

            @Override
            protected List<String> lookup(String key) {
                return nameSet.contains(key) ? Collections.singletonList(key) : Collections.emptyList();
            }
        };
    }
}
