package dev.eministar.nebiupdate.data;

import java.util.Arrays;
import java.util.Optional;

public enum UpdateType {
    ADDED("added", "Neu"),
    CHANGED("changed", "Verändert"),
    REMOVED("removed", "Entfernt");

    private final String key;
    private final String label;

    UpdateType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Optional<UpdateType> fromKey(String key) {
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(key))
                .findFirst();
    }
}
