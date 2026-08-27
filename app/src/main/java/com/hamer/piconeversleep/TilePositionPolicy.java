package com.hamer.piconeversleep;

final class TilePositionPolicy {
    private TilePositionPolicy() {}

    static int normalizedIndex(int current, int wanted, int size) {
        if (size <= 0) return 0;
        int target = Math.max(0, Math.min(wanted, size - 1));
        return current == target ? current : target;
    }
}
