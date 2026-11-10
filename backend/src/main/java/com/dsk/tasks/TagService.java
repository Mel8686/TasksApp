package com.dsk.tasks;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the names a client sends into {@link Tag} rows, creating the ones that
 * do not exist yet. Always called from inside a transaction, so the returned
 * entities stay managed.
 */
@Service
public class TagService {

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    /**
     * @throws IllegalArgumentException if a name breaks {@link TagRules}
     */
    public Set<Tag> resolve(List<String> raw) {
        List<String> names = TagRules.normalize(raw).stream().sorted().toList();
        Map<String, Tag> known = resolveAll(names);
        Set<Tag> resolved = new LinkedHashSet<>();
        for (String name : names) {
            resolved.add(known.get(name));
        }
        return resolved;
    }

    /**
     * One lookup for the whole batch: the import calls this once for every row
     * instead of once per row, so a thousand-row file stays two queries here.
     */
    public Map<String, Tag> resolveAll(Collection<String> names) {
        Map<String, Tag> found = new HashMap<>();
        if (names.isEmpty()) {
            return found;
        }
        tags.findByNameIn(names).forEach(tag -> found.put(tag.getName(), tag));
        for (String name : names) {
            found.computeIfAbsent(name, key -> tags.save(new Tag(key)));
        }
        return found;
    }
}
