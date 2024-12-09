package ADT_Self_Implement;

public class TaskQueue {
    private TotalPath2.PathExplorer[] array;
    private int size;
    private static final int DEFAULT_CAPACITY = 32;  // Increased for fewer resizes
    private static final int SMALL_ARRAY_THRESHOLD = 64;
    private static final float LOAD_FACTOR = 0.75f;

    public TaskQueue() {
        array = new TotalPath2.PathExplorer[DEFAULT_CAPACITY];
        size = 0;
    }

    public void add(TotalPath2.PathExplorer task) {
        // Check if we need to resize before adding
        if (size >= array.length * LOAD_FACTOR) {
            resize(array.length * 2);
        }
        array[size++] = task;
    }

    public TotalPath2.PathExplorer[] getAll() {
        // For very small arrays, direct copy is faster
        if (size < SMALL_ARRAY_THRESHOLD) {
            TotalPath2.PathExplorer[] result = new TotalPath2.PathExplorer[size];
            for (int i = 0; i < size; i++) {
                result[i] = array[i];
            }
            return result;
        }
        return java.util.Arrays.copyOf(array, size);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void resize(int newCapacity) {
        TotalPath2.PathExplorer[] newArray = new TotalPath2.PathExplorer[newCapacity];

        // For small arrays, manual copy is faster than System.arraycopy
        if (size < SMALL_ARRAY_THRESHOLD) {
            for (int i = 0; i < size; i++) {
                newArray[i] = array[i];
            }
        } else {
            System.arraycopy(array, 0, newArray, 0, size);
        }

        // Clear old array references to help GC
        for (int i = 0; i < size; i++) {
            array[i] = null;
        }

        array = newArray;
    }

    // Add bulk operations for better performance when needed
    public void addAll(TotalPath2.PathExplorer[] tasks, int length) {
        ensureCapacity(size + length);
        System.arraycopy(tasks, 0, array, size, length);
        size += length;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > array.length) {
            int newCapacity = Math.max(minCapacity,
                    (int)(array.length * 2));
            resize(newCapacity);
        }
    }

    // Optional: Method to trim excess capacity
    public void trimToSize() {
        if (size < array.length) {
            resize(size);
        }
    }

    // Optional: Clear method that helps with GC
    public void clear() {
        for (int i = 0; i < size; i++) {
            array[i] = null;
        }
        size = 0;
    }
}